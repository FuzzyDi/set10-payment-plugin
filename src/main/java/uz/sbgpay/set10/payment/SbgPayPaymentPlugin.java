package uz.sbgpay.set10.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import ru.crystals.pos.api.comm.CommunicationMessage;
import ru.crystals.pos.api.plugin.PaymentPlugin;
import ru.crystals.pos.api.plugin.payment.Payment;
import ru.crystals.pos.api.ui.listener.ConfirmListener;
import ru.crystals.pos.api.ui.listener.InputListener;
import ru.crystals.pos.api.ui.listener.SumToPayFormListener;

import ru.crystals.pos.spi.IncorrectStateException;
import ru.crystals.pos.spi.IntegrationProperties;
import ru.crystals.pos.spi.POSInfo;
import ru.crystals.pos.spi.PropertiesReader;
import ru.crystals.pos.spi.ResBundle;
import ru.crystals.pos.spi.annotation.Inject;
import ru.crystals.pos.spi.annotation.POSPlugin;
import ru.crystals.pos.spi.equipment.CustomerDisplay;
import ru.crystals.pos.spi.equipment.CustomerDisplayMessage;
import ru.crystals.pos.spi.plugin.payment.CancelRequest;
import ru.crystals.pos.spi.plugin.payment.InvalidPaymentException;
import ru.crystals.pos.spi.plugin.payment.PaymentCallback;
import ru.crystals.pos.spi.plugin.payment.PaymentRequest;
import ru.crystals.pos.spi.plugin.payment.RefundRequest;
import ru.crystals.pos.spi.receipt.LineItem;
import ru.crystals.pos.spi.receipt.Merchandise;
import ru.crystals.pos.spi.receipt.Receipt;
import ru.crystals.pos.spi.ui.UIForms;
import ru.crystals.pos.spi.ui.payment.SumToPayFormParameters;

/**
 * SBG Pay Payment Plugin для Set Retail 10
 * 
 * Полное соответствие спецификации POS API v1:
 * - GET /api/v1/payment-methods — получение методов оплаты
 * - POST /api/v1/payments — создание платежа
 * - GET /api/v1/payments/{id}/status — опрос статуса
 * - POST /api/v1/payments/{id}/cancel — отмена платежа
 * 
 * Флоу:
 * 1. Кассир выбирает способ оплаты (плагин)
 * 2. Плагин запрашивает список методов оплаты
 * 3. Кассир выбирает метод (Click, Payme и т.д.)
 * 4. Кассир вводит сумму к оплате (поддержка частичной оплаты)
 * 5. Плагин создаёт платёж и получает qrCodeData
 * 6. QR отображается на дисплее покупателя
 * 7. Покупатель сканирует и оплачивает
 * 8. Плагин опрашивает статус до completed/failed
 * 9. Чек закрывается
 */
@POSPlugin(id = "uz.sbgpay.payment")
public class SbgPayPaymentPlugin implements PaymentPlugin {

    // ====================
    // ИНЪЕКЦИИ Set API
    // ====================
    
    @Inject
    private Logger log;

    @Inject
    private UIForms uiForms;

    @Inject
    private CustomerDisplay customerDisplay;

    @Inject
    private IntegrationProperties integrationProperties;

    @Inject
    private POSInfo posInfo;

    @Inject
    private ResBundle resBundle;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ====================
    // КОНФИГУРАЦИЯ
    // ====================
    
    private String baseUrl;
    private String deviceToken;
    private String language;
    private String currency;
    private int ttlSeconds;
    private int pollDelayMs;
    private int pollTimeoutSeconds;
    private boolean sendReceipt;
    private String qrUrlTemplate;

    // ====================
    // СОСТОЯНИЕ ПЛАТЕЖА
    // ====================
    
    private volatile String currentPaymentId;
    private volatile String currentPaymentCode;
    private volatile String currentMethodId;
    private volatile String currentMethodName;
    private volatile ScheduledExecutorService statusPoller;
    private volatile boolean paymentInProgress;
    private volatile String currentQrUrl;
    private volatile boolean qrDisplayed;
    private volatile List<PaymentMethod> cachedMethods;

    // ====================
    // PaymentPlugin INTERFACE
    // ====================

    @Override
    public boolean isAvailable() {
        loadConfiguration();
        
        if (baseUrl == null || baseUrl.isEmpty()) {
            log.info("[SBGPay] isAvailable=false: baseUrl not configured");
            return false;
        }
        if (deviceToken == null || deviceToken.isEmpty()) {
            log.info("[SBGPay] isAvailable=false: deviceToken not configured");
            return false;
        }
        
        log.info("[SBGPay] isAvailable=true");
        return true;
    }

    @Override
    public void doPayment(PaymentRequest request) {
        log.info("[SBGPay] ===== PAYMENT START =====");
        log.info("[SBGPay] Shop={}, POS={}, Receipt={}",
            posInfo != null ? posInfo.getShopNumber() : "?",
            posInfo != null ? posInfo.getPOSNumber() : "?",
            request.getReceipt().getNumber());

        loadConfiguration();

        if (!isAvailable()) {
            log.error("[SBGPay] Plugin not available");
            request.getPaymentCallback().paymentNotCompleted();
            return;
        }

        // Сброс состояния
        resetState();

        final PaymentCallback callback = request.getPaymentCallback();
        final Receipt receipt = request.getReceipt();
        final BigDecimal amount = receipt.getSurchargeSum();

        log.info("[SBGPay] Amount to pay: {} {}", amount, currency);

        // Показываем спиннер и загружаем методы оплаты
        showSpinner(getString("loading.methods", "Загрузка методов оплаты..."));

        new Thread(() -> {
            try {
                List<PaymentMethod> methods = fetchPaymentMethods();
                
                if (methods.isEmpty()) {
                    SwingUtilities.invokeLater(() -> 
                        showErrorAndAbort(getString("error.no.methods", "Нет доступных методов оплаты"), callback));
                    return;
                }

                log.info("[SBGPay] Loaded {} payment methods", methods.size());
                SwingUtilities.invokeLater(() -> 
                    showMethodSelectionForm(methods, callback, receipt, amount));

            } catch (Exception e) {
                log.error("[SBGPay] Failed to load payment methods", e);
                SwingUtilities.invokeLater(() -> 
                    showErrorAndAbort(getString("error.load.methods", "Ошибка загрузки методов: ") + e.getMessage(), callback));
            }
        }, "sbgpay-load-methods").start();
    }

    @Override
    public void doPaymentCancel(CancelRequest request) {
        log.info("[SBGPay] Payment cancel requested, paymentId={}", currentPaymentId);
        
        stopStatusPolling();
        clearCustomerDisplay();

        if (currentPaymentId != null) {
            new Thread(() -> {
                try {
                    cancelPaymentOnServer(currentPaymentId);
                    log.info("[SBGPay] Payment cancelled on server");
                } catch (Exception e) {
                    log.warn("[SBGPay] Cancel request failed: {}", e.getMessage());
                }
            }, "sbgpay-cancel").start();
        }

        request.getPaymentCallback().paymentNotCompleted();
    }

    @Override
    public void doRefund(RefundRequest request) {
        log.warn("[SBGPay] Refund not supported");
        
        clearCustomerDisplay();
        showCustomerText(getString("refund.not.supported", "Возврат не поддерживается"));

        try {
            uiForms.showMessageForm(
                getString("refund.not.supported", "Возврат через SBG Pay не поддерживается"),
                new ConfirmListener() {
                    @Override
                    public void eventConfirmed() {
                        request.getPaymentCallback().paymentNotCompleted();
                    }
                });
        } catch (IncorrectStateException e) {
            request.getPaymentCallback().paymentNotCompleted();
        }
    }

    // ====================
    // PAYMENT FLOW
    // ====================

    /**
     * Показывает форму выбора метода оплаты
     */
    private void showMethodSelectionForm(List<PaymentMethod> methods,
                                         PaymentCallback callback,
                                         Receipt receipt,
                                         BigDecimal amount) {
        // Сохраняем для возможности возврата при отмене ввода суммы
        this.cachedMethods = methods;
        
        // Формируем данные для таблицы выбора
        Map<String, List<String>> items = new LinkedHashMap<>();
        
        for (PaymentMethod method : methods) {
            List<String> columns = new ArrayList<>();
            columns.add(method.name != null ? method.name : method.methodId);
            columns.add(method.providerName != null ? method.providerName : "");
            columns.add(formatAmountLimits(method));
            items.put(method.methodId, columns);
        }

        try {
            uiForms.getInputForms().showSelectionForm(
                getString("select.method", "SBG Pay: выберите способ оплаты"),
                items,
                new InputListener() {
                    @Override
                    public void eventInputComplete(String selectedMethodId) {
                        log.info("[SBGPay] Selected method: {}", selectedMethodId);
                        
                        PaymentMethod selected = findMethodById(methods, selectedMethodId);
                        if (selected == null) {
                            showErrorAndAbort(getString("error.method.not.found", "Метод не найден"), callback);
                            return;
                        }

                        // Сохраняем выбранный метод
                        currentMethodId = selected.methodId;
                        currentMethodName = selected.name;

                        // Показываем форму ввода суммы
                        showAmountInputForm(selected, callback, receipt, amount);
                    }

                    @Override
                    public void eventCanceled() {
                        log.info("[SBGPay] Method selection cancelled");
                        clearCustomerDisplay();
                        callback.paymentNotCompleted();
                    }
                });
        } catch (Exception e) {
            log.error("[SBGPay] Failed to show selection form", e);
            callback.paymentNotCompleted();
        }
    }

    /**
     * Показывает форму ввода суммы к оплате
     */
    private void showAmountInputForm(PaymentMethod method,
                                      PaymentCallback callback,
                                      Receipt receipt,
                                      BigDecimal defaultSum) {
        
        String title = (method.name != null && !method.name.isEmpty()) 
            ? method.name 
            : getString("payment.name", "SBG Pay");

        SumToPayFormParameters parameters = new SumToPayFormParameters(title, receipt);
        parameters.setInputHint(getString("enter.sum.to.pay", "Введите сумму к оплате"));
        parameters.setDefaultSum(defaultSum);

        try {
            uiForms.getPaymentForms().showSumToPayForm(parameters, new SumToPayFormListener() {
                @Override
                public void eventCanceled() {
                    log.info("[SBGPay] Amount input cancelled, returning to method selection");
                    showMethodSelectionForm(cachedMethods, callback, receipt, defaultSum);
                }

                @Override
                public void eventSumEntered(BigDecimal enteredAmount) {
                    log.info("[SBGPay] Amount entered: {}", enteredAmount);
                    
                    // Валидация суммы по лимитам метода
                    String validationError = validateAmountForMethod(enteredAmount, method);
                    if (validationError != null) {
                        showAmountValidationError(validationError, method, callback, receipt, defaultSum);
                        return;
                    }

                    // Создаём платёж на введённую сумму
                    createPayment(method, callback, receipt, enteredAmount);
                }
            });
        } catch (IncorrectStateException e) {
            log.error("[SBGPay] Failed to show amount input form", e);
            callback.paymentNotCompleted();
        }
    }

    /**
     * Валидация суммы по лимитам метода оплаты
     * @return null если OK, иначе текст ошибки
     */
    private String validateAmountForMethod(BigDecimal amount, PaymentMethod method) {
        long amountMinor = toMinorUnits(amount);
        
        if (method.minAmount > 0 && amountMinor < method.minAmount) {
            return getString("error.amount.too.small", "Сумма меньше минимальной: ") 
                + fromMinorUnits(method.minAmount) + " " + currency;
        }
        
        if (method.maxAmount > 0 && amountMinor > method.maxAmount) {
            return getString("error.amount.too.large", "Сумма больше максимальной: ") 
                + fromMinorUnits(method.maxAmount) + " " + currency;
        }
        
        return null;
    }

    /**
     * Показывает ошибку валидации суммы и возвращает к вводу суммы
     */
    private void showAmountValidationError(String errorMessage,
                                            PaymentMethod method,
                                            PaymentCallback callback,
                                            Receipt receipt,
                                            BigDecimal defaultSum) {
        try {
            uiForms.showErrorForm(errorMessage, new ConfirmListener() {
                @Override
                public void eventConfirmed() {
                    showAmountInputForm(method, callback, receipt, defaultSum);
                }
            });
        } catch (IncorrectStateException e) {
            log.error("[SBGPay] Failed to show error form", e);
            callback.paymentNotCompleted();
        }
    }

    /**
     * Создаёт платёж через API и запускает отображение QR + polling
     */
    private void createPayment(PaymentMethod method,
                               PaymentCallback callback,
                               Receipt receipt,
                               BigDecimal amount) {
        
        paymentInProgress = true;
        currentMethodId = method.methodId;
        currentMethodName = method.name;

        showSpinner(getString("creating.payment", "Создание платежа..."));
        showCustomerText(currentMethodName + "\n" + getString("preparing", "Подготовка..."));

        new Thread(() -> {
            try {
                CreatePaymentResponse response = createPaymentOnServer(method, receipt, amount);

                currentPaymentId = response.paymentId;
                currentPaymentCode = response.paymentCode;

                log.info("[SBGPay] Payment created: id={}, code={}, status={}", 
                    currentPaymentId, currentPaymentCode, response.status);

                SwingUtilities.invokeLater(() -> {
                    showWaitingForPaymentSpinner();
                });

                startStatusPolling(callback, amount);

            } catch (Exception e) {
                log.error("[SBGPay] Failed to create payment", e);
                SwingUtilities.invokeLater(() -> 
                    showErrorAndAbort(getString("error.create.payment", "Ошибка создания платежа: ") + e.getMessage(), callback));
            }
        }, "sbgpay-create-payment").start();
    }

    /**
     * Запускает периодический опрос статуса платежа
     */
    private void startStatusPolling(PaymentCallback callback, BigDecimal amount) {
        stopStatusPolling();

        if (currentPaymentId == null) {
            log.error("[SBGPay] Cannot start polling: paymentId is null");
            return;
        }

        final long startTime = System.currentTimeMillis();
        final long timeoutMs = pollTimeoutSeconds * 1000L;

        statusPoller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sbgpay-status-poller");
            t.setDaemon(true);
            return t;
        });

        log.info("[SBGPay] Starting status polling: paymentId={}, interval={}ms, timeout={}s",
            currentPaymentId, pollDelayMs, pollTimeoutSeconds);

        statusPoller.scheduleWithFixedDelay(() -> {
            try {
                if (!paymentInProgress || currentPaymentId == null) {
                    return;
                }

                long elapsed = System.currentTimeMillis() - startTime;

                if (elapsed > timeoutMs) {
                    log.warn("[SBGPay] Payment timeout after {}ms", elapsed);
                    stopStatusPolling();
                    SwingUtilities.invokeLater(() -> 
                        showErrorAndAbort(getString("error.timeout", "Время ожидания оплаты истекло"), callback));
                    return;
                }

                PaymentStatus status = fetchPaymentStatus(currentPaymentId);
                log.debug("[SBGPay] Status poll: status={}, hasQrPayload={}, elapsed={}ms", 
                    status.status, (status.qrPayload != null && !status.qrPayload.isEmpty()), elapsed);

                if (!qrDisplayed && status.qrPayload != null && !status.qrPayload.isEmpty()) {
                    qrDisplayed = true;
                    log.info("[SBGPay] Received qrPayload, showing on customer display");
                    final String qrData = status.qrPayload;
                    SwingUtilities.invokeLater(() -> showQrOnCustomerDisplay(amount, qrData));
                }

                if (isSuccessStatus(status.status)) {
                    log.info("[SBGPay] Payment completed successfully");
                    stopStatusPolling();
                    SwingUtilities.invokeLater(() -> completePayment(callback, amount, status));
                    
                } else if (isFailedStatus(status.status)) {
                    String errorDetail = (status.errorMessage != null && !status.errorMessage.isEmpty()) 
                        ? status.errorMessage 
                        : status.status;
                    log.warn("[SBGPay] Payment failed: status={}, error={}", status.status, status.errorMessage);
                    stopStatusPolling();
                    SwingUtilities.invokeLater(() -> 
                        showErrorAndAbort(getString("error.payment.failed", "Оплата не выполнена: ") + errorDetail, callback));
                }

            } catch (Exception e) {
                log.error("[SBGPay] Status polling error", e);
            }
        }, pollDelayMs, pollDelayMs, TimeUnit.MILLISECONDS);
    }

    private void stopStatusPolling() {
        if (statusPoller != null) {
            try {
                statusPoller.shutdownNow();
            } catch (Exception ignored) {}
            statusPoller = null;
            log.debug("[SBGPay] Status polling stopped");
        }
    }

    /**
     * Завершает платёж успешно
     */
    private void completePayment(PaymentCallback callback, BigDecimal amount, PaymentStatus status) {
        paymentInProgress = false;

        String title = (currentMethodName != null && !currentMethodName.isEmpty()) 
            ? currentMethodName 
            : "SBG Pay";

        clearCustomerDisplay();
        showCustomerText(title + "\n" + getString("payment.success", "Оплата успешна!"));

        Payment payment = new Payment();
        payment.setSum(amount);
        
        payment.getData().put("sbgpay.paymentId", nullToEmpty(currentPaymentId));
        payment.getData().put("sbgpay.paymentCode", nullToEmpty(currentPaymentCode));
        payment.getData().put("sbgpay.methodId", nullToEmpty(currentMethodId));
        payment.getData().put("sbgpay.methodName", nullToEmpty(currentMethodName));
        payment.getData().put("sbgpay.status", nullToEmpty(status.status));

        log.info("[SBGPay] ===== PAYMENT COMPLETED =====");
        log.info("[SBGPay] paymentId={}, method={}, status={}, amount={}", 
            currentPaymentId, currentMethodName, status.status, amount);

        try {
            callback.paymentCompleted(payment);
        } catch (InvalidPaymentException e) {
            log.error("[SBGPay] Payment rejected by POS", e);
            callback.paymentNotCompleted();
        }
    }

    // ====================
    // API METHODS
    // ====================

    /**
     * GET /api/v1/payment-methods
     */
    private List<PaymentMethod> fetchPaymentMethods() throws Exception {
        String url = baseUrl + "/api/v1/payment-methods?lang=" + urlEncode(language) + "&currency=" + urlEncode(currency);
        
        JsonNode root = httpGet(url);
        List<PaymentMethod> methods = new ArrayList<>();

        log.debug("[SBGPay] Raw payment-methods response: {}", root.toString());
        
        JsonNode methodsArray = root.get("methods");
        if (methodsArray == null && root.isArray()) {
            log.debug("[SBGPay] Response is array directly (no 'methods' wrapper)");
            methodsArray = root;
        }
        
        if (methodsArray == null) {
            log.warn("[SBGPay] No 'methods' field in response. Keys: {}", root.fieldNames());
            return methods;
        }
        
        if (!methodsArray.isArray()) {
            log.warn("[SBGPay] 'methods' is not an array: {}", methodsArray.getNodeType());
            return methods;
        }
        
        log.debug("[SBGPay] Found {} items in methods array", methodsArray.size());
        for (JsonNode node : methodsArray) {
            PaymentMethod m = new PaymentMethod();
            
            m.methodId = getText(node, "methodId");
            if (m.methodId == null || m.methodId.isEmpty()) {
                m.methodId = getText(node, "id");
            }
            
            m.name = getText(node, "name");
            if (m.name == null || m.name.isEmpty()) {
                m.name = getText(node, "title");
            }
            
            m.description = getText(node, "description");
            m.kind = getText(node, "kind");
            m.iconUrl = getText(node, "iconUrl");
            m.currency = getText(node, "currency");
            
            m.minAmount = getLong(node, "minAmount", 0);
            m.maxAmount = getLong(node, "maxAmount", 0);
            
            JsonNode providerNode = node.get("provider");
            if (providerNode != null) {
                if (providerNode.isObject()) {
                    m.providerCode = getText(providerNode, "code");
                    m.providerName = getText(providerNode, "name");
                } else if (providerNode.isTextual()) {
                    m.providerCode = providerNode.asText();
                    m.providerName = providerNode.asText();
                }
            }

            if (m.methodId != null && !m.methodId.isEmpty()) {
                methods.add(m);
                log.debug("[SBGPay] Parsed method: id={}, name={}, kind={}", m.methodId, m.name, m.kind);
            } else {
                log.warn("[SBGPay] Skipped method with empty methodId. Node: {}", node.toString());
            }
        }

        return methods;
    }

    /**
     * POST /api/v1/payments
     */
    private CreatePaymentResponse createPaymentOnServer(PaymentMethod method, 
                                                         Receipt receipt, 
                                                         BigDecimal amount) throws Exception {
        String url = baseUrl + "/api/v1/payments";
        String idempotencyKey = UUID.randomUUID().toString();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("methodId", method.methodId);

        Map<String, Object> amountObj = new LinkedHashMap<>();
        amountObj.put("value", toMinorUnits(amount));
        amountObj.put("currency", currency);
        body.put("amount", amountObj);

        Map<String, Object> orderObj = new LinkedHashMap<>();
        orderObj.put("id", generateOrderId(receipt));
        body.put("order", orderObj);

        body.put("ttlSeconds", ttlSeconds);
        body.put("lang", language);

        log.debug("[SBGPay] Receipt check: sendReceipt={}, lineItems={}, isEmpty={}", 
            sendReceipt, 
            receipt.getLineItems() != null ? receipt.getLineItems().size() : "null",
            receipt.getLineItems() != null ? receipt.getLineItems().isEmpty() : "null");
        
        if (sendReceipt && receipt.getLineItems() != null && !receipt.getLineItems().isEmpty()) {
            Map<String, Object> receiptObj = buildReceiptObject(receipt);
            body.put("receipt", receiptObj);
            log.info("[SBGPay] Sending receipt with {} items", receipt.getLineItems().size());
        } else if (sendReceipt) {
            log.warn("[SBGPay] sendReceipt=true but no line items in receipt");
        }

        JsonNode response = httpPost(url, body, idempotencyKey);

        CreatePaymentResponse result = new CreatePaymentResponse();
        result.paymentId = getText(response, "paymentId");
        if (result.paymentId == null) result.paymentId = getText(response, "id");
        
        result.paymentCode = getText(response, "paymentCode");
        if (result.paymentCode == null) result.paymentCode = getText(response, "code");
        
        result.status = getText(response, "status");
        
        result.qrCodeData = getQrFromResponse(response);

        if (result.paymentId == null || result.paymentId.isEmpty()) {
            throw new IllegalStateException("No paymentId in response");
        }

        return result;
    }

    /**
     * GET /api/v1/payments/{id}/status
     */
    private PaymentStatus fetchPaymentStatus(String paymentId) throws Exception {
        String url = baseUrl + "/api/v1/payments/" + urlEncode(paymentId) + "/status";
        
        JsonNode response = httpGet(url);
        
        log.debug("[SBGPay] Status response: {}", response.toString());

        PaymentStatus status = new PaymentStatus();
        status.paymentId = getText(response, "paymentId");
        status.paymentCode = getText(response, "paymentCode");
        status.status = getText(response, "status");
        
        status.qrPayload = getText(response, "qrPayload");
        
        status.qrCodeData = getText(response, "qrCodeData");
        
        status.errorMessage = getText(response, "errorMessage");
        if (status.errorMessage == null) {
            status.errorMessage = getText(response, "error");
        }
        if (status.errorMessage == null) {
            status.errorMessage = getText(response, "message");
        }

        return status;
    }

    /**
     * POST /api/v1/payments/{id}/cancel
     */
    private void cancelPaymentOnServer(String paymentId) throws Exception {
        String url = baseUrl + "/api/v1/payments/" + urlEncode(paymentId) + "/cancel";
        httpPost(url, new LinkedHashMap<>(), UUID.randomUUID().toString());
    }

    /**
     * Формирует объект receipt согласно спецификации
     */
    private Map<String, Object> buildReceiptObject(Receipt receipt) {
        Map<String, Object> receiptObj = new LinkedHashMap<>();
        
        List<Map<String, Object>> items = new ArrayList<>();
        int lineId = 1;
        
        for (LineItem item : receipt.getLineItems()) {
            Map<String, Object> itemObj = new LinkedHashMap<>();
            itemObj.put("lineId", String.valueOf(lineId++));
            
            Merchandise merchandise = item.getMerchandise();
            if (merchandise != null) {
                itemObj.put("name", nullToEmpty(merchandise.getName()));
                itemObj.put("sku", nullToEmpty(merchandise.getMarking()));
                
                String barcode = merchandise.getBarcode();
                itemObj.put("barcode", (barcode != null && !barcode.isEmpty()) ? barcode : null);
                
                itemObj.put("price", toMinorUnits(merchandise.getPrice()));
            } else {
                itemObj.put("name", "Unknown");
                itemObj.put("sku", "");
                itemObj.put("barcode", null);
                itemObj.put("price", 0L);
            }
            
            itemObj.put("qty", item.getQuantity() / 1000.0);
            itemObj.put("total", toMinorUnits(item.getSum()));
            
            // Добавляем единицу измерения
            String unit = mapMeasureUnit(item);
            itemObj.put("unit", unit);
            
            items.add(itemObj);
        }
        
        receiptObj.put("items", items);
        return receiptObj;
    }

    /**
     * Маппинг единицы измерения из Set Retail 10 в формат SBG Pay
     */
    private String mapMeasureUnit(LineItem item) {
        // Пробуем получить код ОКЕИ
        String rcumCode = item.getMeasureRcumCode();
        if (rcumCode != null && !rcumCode.isEmpty()) {
            switch (rcumCode) {
                case "166": return "kg";   // килограмм
                case "163": return "g";    // грамм
                case "112": return "l";    // литр
                case "006": return "m";    // метр
                case "796": return "pcs";  // штуки
                default: break;
            }
        }
        
        // Пробуем по названию единицы измерения
        String measureName = item.getMeasureName();
        if (measureName != null && !measureName.isEmpty()) {
            String nameLower = measureName.toLowerCase().trim();
            
            // Килограмм
            if (nameLower.equals("кг") || nameLower.equals("килограмм") || 
                nameLower.equals("kg") || nameLower.equals("kilogram")) {
                return "kg";
            }
            
            // Грамм
            if (nameLower.equals("г") || nameLower.equals("грамм") || 
                nameLower.equals("g") || nameLower.equals("gr") || nameLower.equals("gram")) {
                return "g";
            }
            
            // Литр
            if (nameLower.equals("л") || nameLower.equals("литр") || 
                nameLower.equals("l") || nameLower.equals("liter") || nameLower.equals("litr")) {
                return "l";
            }
            
            // Метр
            if (nameLower.equals("м") || nameLower.equals("метр") || 
                nameLower.equals("m") || nameLower.equals("meter") || nameLower.equals("metr")) {
                return "m";
            }
            
            // Штуки (русский, узбекский кириллица, узбекский латиница, английский)
            if (nameLower.equals("шт") || nameLower.equals("штука") || nameLower.equals("штуки") ||
                nameLower.equals("дона") ||                          // узбекский кириллица
                nameLower.equals("dona") ||                          // узбекский латиница
                nameLower.equals("pcs") || nameLower.equals("piece") || nameLower.equals("pieces")) {
                return "pcs";
            }
        }
        
        // По умолчанию — штуки
        log.debug("[SBGPay] Unknown measure unit: rcumCode={}, measureName={}, defaulting to 'pcs'", 
            rcumCode, measureName);
        return "pcs";
    }

    // ====================
    // HTTP CLIENT
    // ====================

    private JsonNode httpGet(String url) throws Exception {
        long startTime = System.currentTimeMillis();
        
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Device-Token", deviceToken);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        try {
            int status = conn.getResponseCode();
            String body = readResponseBody(conn, status);
            
            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("[HTTP] GET {} -> {} ({}ms)", url, status, elapsed);

            if (status >= 400) {
                handleHttpError(status, body);
            }

            return objectMapper.readTree(body != null ? body : "{}");
        } finally {
            conn.disconnect();
        }
    }

    private JsonNode httpPost(String url, Map<String, Object> body, String idempotencyKey) throws Exception {
        long startTime = System.currentTimeMillis();
        
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Device-Token", deviceToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Idempotency-Key", idempotencyKey);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        conn.setDoOutput(true);

        byte[] payload = objectMapper.writeValueAsBytes(body);
        
        log.debug("[HTTP] POST {} Request body: {}", url, new String(payload, "UTF-8"));
        
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }

        try {
            int status = conn.getResponseCode();
            String responseBody = readResponseBody(conn, status);
            
            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("[HTTP] POST {} -> {} ({}ms)", url, status, elapsed);
            
            log.debug("[HTTP] POST {} Response body: {}", url, responseBody);

            if (status >= 400) {
                handleHttpError(status, responseBody);
            }

            return objectMapper.readTree(responseBody != null ? responseBody : "{}");
        } finally {
            conn.disconnect();
        }
    }

    private String readResponseBody(HttpURLConnection conn, int status) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                status < 400 ? conn.getInputStream() : conn.getErrorStream(), StandardCharsets.UTF_8))) {
            
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private void handleHttpError(int status, String body) throws Exception {
        String detail = "HTTP " + status;
        
        try {
            JsonNode error = objectMapper.readTree(body);
            if (error.has("detail")) {
                detail = error.get("detail").asText();
            } else if (error.has("title")) {
                detail = error.get("title").asText();
            }
        } catch (Exception ignored) {}

        if (status == 401) {
            throw new Exception(getString("error.unauthorized", "Ошибка авторизации: проверьте Device-Token"));
        } else if (status == 429) {
            throw new Exception(getString("error.rate.limit", "Слишком много запросов, попробуйте позже"));
        } else {
            throw new Exception(detail);
        }
    }

    // ====================
    // CUSTOMER DISPLAY
    // ====================

    private void showQrOnCustomerDisplay(BigDecimal amount, String qrCodeData) {
        if (customerDisplay == null) {
            log.warn("[SBGPay] CustomerDisplay not available");
            return;
        }
        if (qrCodeData == null || qrCodeData.isEmpty()) {
            log.warn("[SBGPay] qrCodeData is empty");
            return;
        }

        try {
            boolean canShowQr = customerDisplay.canShowQr();
            
            String title = (currentMethodName != null && !currentMethodName.isEmpty()) 
                ? currentMethodName 
                : "SBG Pay";
            
            log.info("[SBGPay] Showing QR on customer display: canShowQr={}, title={}, qrData.length={}", 
                canShowQr, title, qrCodeData.length());

            customerDisplay.clear();

            if (canShowQr) {
                CommunicationMessage message = new CommunicationMessage(
                    null,
                    null,
                    title,
                    getString("scan.qr", "Сканируйте QR для оплаты"),
                    qrCodeData,
                    amount
                );
                message.setAutoCloseable(false);

                Duration displayDuration = Duration.ofSeconds(Math.min(ttlSeconds, pollTimeoutSeconds));
                CustomerDisplayMessage displayMessage = new CustomerDisplayMessage(message, displayDuration);
                
                customerDisplay.display(displayMessage);
                log.info("[SBGPay] QR displayed on customer display");
            } else {
                String text = title + "\n" + getString("amount", "К оплате: ") + amount + " " + currency;
                customerDisplay.setText(text);
                log.info("[SBGPay] Text displayed on customer display (no QR support)");
            }
        } catch (Exception e) {
            log.error("[SBGPay] Failed to show QR on customer display", e);
        }
    }

    private void showCustomerText(String text) {
        if (customerDisplay == null) return;
        try {
            customerDisplay.setText(text);
        } catch (Exception e) {
            log.warn("[SBGPay] Failed to set customer display text", e);
        }
    }

    private void clearCustomerDisplay() {
        if (customerDisplay == null) return;
        try {
            customerDisplay.clear();
        } catch (Exception e) {
            log.warn("[SBGPay] Failed to clear customer display", e);
        }
    }

    // ====================
    // UI HELPERS
    // ====================

    private void showSpinner(String text) {
        SwingUtilities.invokeLater(() -> {
            try {
                uiForms.showSpinnerForm(text);
            } catch (Exception e) {
                log.warn("[SBGPay] Failed to show spinner", e);
            }
        });
    }

    private void showWaitingForPaymentSpinner() {
        String text = getString("waiting.payment", "Ожидание оплаты...");
        if (currentPaymentCode != null && !currentPaymentCode.isEmpty()) {
            text += "\n" + getString("payment.code", "Код: ") + currentPaymentCode;
        }
        showSpinner(text);
    }

    private void showErrorAndAbort(String message, PaymentCallback callback) {
        paymentInProgress = false;
        stopStatusPolling();
        clearCustomerDisplay();

        log.warn("[SBGPay] Aborting: {}", message);

        try {
            uiForms.showMessageForm(message, new ConfirmListener() {
                @Override
                public void eventConfirmed() {
                    callback.paymentNotCompleted();
                }
            });
        } catch (IncorrectStateException e) {
            callback.paymentNotCompleted();
        }
    }

    // ====================
    // UTILITIES
    // ====================

    private void loadConfiguration() {
        try {
            PropertiesReader props = integrationProperties.getServiceProperties();
            
            baseUrl = props.get("sbgpay.baseUrl", "https://sbg.amasia.io/pos");
            deviceToken = props.get("sbgpay.deviceToken");
            language = props.get("sbgpay.lang", "ru");
            currency = props.get("sbgpay.currency", "UZS");
            ttlSeconds = props.getInt("sbgpay.ttlSeconds", 300);
            pollDelayMs = props.getInt("sbgpay.pollDelayMs", 2000);
            pollTimeoutSeconds = props.getInt("sbgpay.pollTimeoutSeconds", 420);
            sendReceipt = props.getBoolean("sbgpay.sendReceipt", false);
            
            qrUrlTemplate = props.get("sbgpay.qrUrlTemplate", "https://sbg.amasia.io/emulators/pay/{paymentId}");

            log.debug("[SBGPay] Config: baseUrl={}, lang={}, currency={}, ttl={}, pollDelay={}, pollTimeout={}, sendReceipt={}",
                baseUrl, language, currency, ttlSeconds, pollDelayMs, pollTimeoutSeconds, sendReceipt);
        } catch (Exception e) {
            log.error("[SBGPay] Failed to load configuration", e);
        }
    }

    private void resetState() {
        stopStatusPolling();
        currentPaymentId = null;
        currentPaymentCode = null;
        currentMethodId = null;
        currentMethodName = null;
        currentQrUrl = null;
        qrDisplayed = false;
        paymentInProgress = false;
        cachedMethods = null;
        clearCustomerDisplay();
    }

    private String getString(String key, String defaultValue) {
        if (resBundle != null) {
            try {
                String value = resBundle.getString(key);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            } catch (Exception ignored) {}
        }
        return defaultValue;
    }

    private long toMinorUnits(BigDecimal amount) {
        return amount.multiply(new BigDecimal(100)).longValue();
    }

    private BigDecimal fromMinorUnits(long minorUnits) {
        return new BigDecimal(minorUnits).divide(new BigDecimal(100));
    }

    private String generateOrderId(Receipt receipt) {
        return "sr10_" + receipt.getNumber() + "_" + System.currentTimeMillis();
    }

    private String formatAmountLimits(PaymentMethod method) {
        if (method.minAmount > 0 && method.maxAmount > 0) {
            return fromMinorUnits(method.minAmount) + " - " + fromMinorUnits(method.maxAmount);
        } else if (method.maxAmount > 0) {
            return "до " + fromMinorUnits(method.maxAmount);
        }
        return "";
    }

    private PaymentMethod findMethodById(List<PaymentMethod> methods, String methodId) {
        for (PaymentMethod m : methods) {
            if (methodId.equals(m.methodId)) {
                return m;
            }
        }
        return null;
    }

    private String getQrFromResponse(JsonNode response) {
        String[] qrFields = {"qrPayload", "qrCodeData", "qrData", "qr"};
        for (String field : qrFields) {
            String value = getText(response, field);
            if (value != null && !value.isEmpty()) {
                log.debug("[SBGPay] Found QR in field '{}': length={}", field, value.length());
                return value;
            }
        }
        return null;
    }

    private boolean isSuccessStatus(String status) {
        return "completed".equalsIgnoreCase(status) || "paid".equalsIgnoreCase(status);
    }

    private boolean isFailedStatus(String status) {
        return "failed".equalsIgnoreCase(status) 
            || "declined".equalsIgnoreCase(status)
            || "cancelled".equalsIgnoreCase(status)
            || "canceled".equalsIgnoreCase(status)
            || "expired".equalsIgnoreCase(status);
    }

    private static String getText(JsonNode node, String key) {
        if (node != null && node.has(key) && !node.get(key).isNull()) {
            return node.get(key).asText();
        }
        return null;
    }

    private static long getLong(JsonNode node, String key, long defaultValue) {
        if (node != null && node.has(key) && !node.get(key).isNull()) {
            return node.get(key).asLong(defaultValue);
        }
        return defaultValue;
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s != null ? s : "", "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    // ====================
    // DTO CLASSES
    // ====================

    private static class PaymentMethod {
        String methodId;
        String name;
        String description;
        String kind;
        String iconUrl;
        String currency;
        long minAmount;
        long maxAmount;
        String providerCode;
        String providerName;
    }

    private static class CreatePaymentResponse {
        String paymentId;
        String paymentCode;
        String status;
        String qrCodeData;
    }

    private static class PaymentStatus {
        String paymentId;
        String paymentCode;
        String status;
        String qrPayload;
        String qrCodeData;
        String errorMessage;
    }
}
