package uz.sbgpay.set10.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import ru.crystals.pos.api.comm.CommunicationMessage;
import ru.crystals.pos.api.plugin.PaymentPlugin;
import ru.crystals.pos.api.plugin.RefundPreparationPlugin;
import ru.crystals.pos.api.plugin.TransactionalRefundPlugin;
import ru.crystals.pos.api.plugin.payment.Payment;
import ru.crystals.pos.api.plugin.payment.PaymentResultData;
import ru.crystals.pos.api.plugin.payment.RefundPreparationResult;
import ru.crystals.pos.api.plugin.payment.TransactionalRefundResult;
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
import ru.crystals.pos.spi.feedback.Feedback;
import ru.crystals.pos.spi.plugin.payment.CancelRequest;
import ru.crystals.pos.spi.plugin.payment.InvalidPaymentException;
import ru.crystals.pos.spi.plugin.payment.PaymentCallback;
import ru.crystals.pos.spi.plugin.payment.PaymentRequest;
import ru.crystals.pos.spi.plugin.payment.PaymentToRefund;
import ru.crystals.pos.spi.plugin.payment.RefundPreparationRequest;
import ru.crystals.pos.spi.plugin.payment.RefundRequest;
import ru.crystals.pos.spi.plugin.payment.TransactionalRefundRequest;
import ru.crystals.pos.spi.receipt.LineItem;
import ru.crystals.pos.spi.receipt.Merchandise;
import ru.crystals.pos.spi.receipt.ProcessedPayment;
import ru.crystals.pos.spi.receipt.Receipt;
import ru.crystals.pos.spi.ui.UIForms;
import ru.crystals.pos.spi.ui.payment.SumToPayFormParameters;

/**
 * SBG Pay Payment Plugin Р Т‘Р В»РЎРЏ Set Retail 10
 *
 * Р СџР С•Р В»Р Р…Р С•Р Вµ РЎРѓР С•Р С•РЎвЂљР Р†Р ВµРЎвЂљРЎРѓРЎвЂљР Р†Р С‘Р Вµ РЎРѓР С—Р ВµРЎвЂ Р С‘РЎвЂћР С‘Р С”Р В°РЎвЂ Р С‘Р С‘ POS API v1:
 * - GET /api/v1/payment-methods РІР‚вЂќ Р С—Р С•Р В»РЎС“РЎвЂЎР ВµР Р…Р С‘Р Вµ Р СР ВµРЎвЂљР С•Р Т‘Р С•Р Р† Р С•Р С—Р В»Р В°РЎвЂљРЎвЂ№
 * - POST /api/v1/payments РІР‚вЂќ РЎРѓР С•Р В·Р Т‘Р В°Р Р…Р С‘Р Вµ Р С—Р В»Р В°РЎвЂљР ВµР В¶Р В°
 * - GET /api/v1/payments/{id}/status РІР‚вЂќ Р С•Р С—РЎР‚Р С•РЎРѓ РЎРѓРЎвЂљР В°РЎвЂљРЎС“РЎРѓР В°
 * - POST /api/v1/payments/{id}/complete РІР‚вЂќ Р С—Р С•Р Т‘РЎвЂљР Р†Р ВµРЎР‚Р В¶Р Т‘Р ВµР Р…Р С‘Р Вµ Р С—Р В»Р В°РЎвЂљР ВµР В¶Р В° Р С—Р С•РЎРѓР В»Р Вµ РЎвЂћР С‘РЎРѓР С”Р В°Р В»Р С‘Р В·Р В°РЎвЂ Р С‘Р С‘
 * - POST /api/v1/payments/{id}/cancel РІР‚вЂќ Р С•РЎвЂљР СР ВµР Р…Р В° Р С—Р В»Р В°РЎвЂљР ВµР В¶Р В°
 * - POST /api/v1/payments/{id}/reversal РІР‚вЂќ Р Р†Р С•Р В·Р Р†РЎР‚Р В°РЎвЂљ Р С—Р В»Р В°РЎвЂљР ВµР В¶Р В°
 *
 * Р В¤Р В»Р С•РЎС“:
 * 1. Р С™Р В°РЎРѓРЎРѓР С‘РЎР‚ Р Р†РЎвЂ№Р В±Р С‘РЎР‚Р В°Р ВµРЎвЂљ РЎРѓР С—Р С•РЎРѓР С•Р В± Р С•Р С—Р В»Р В°РЎвЂљРЎвЂ№ (Р С—Р В»Р В°Р С–Р С‘Р Р…)
 * 2. Р СџР В»Р В°Р С–Р С‘Р Р… Р В·Р В°Р С—РЎР‚Р В°РЎв‚¬Р С‘Р Р†Р В°Р ВµРЎвЂљ РЎРѓР С—Р С‘РЎРѓР С•Р С” Р СР ВµРЎвЂљР С•Р Т‘Р С•Р Р† Р С•Р С—Р В»Р В°РЎвЂљРЎвЂ№
 * 3. Р С™Р В°РЎРѓРЎРѓР С‘РЎР‚ Р Р†РЎвЂ№Р В±Р С‘РЎР‚Р В°Р ВµРЎвЂљ Р СР ВµРЎвЂљР С•Р Т‘ (Click, Payme Р С‘ РЎвЂљ.Р Т‘.)
 * 4. Р С™Р В°РЎРѓРЎРѓР С‘РЎР‚ Р Р†Р Р†Р С•Р Т‘Р С‘РЎвЂљ РЎРѓРЎС“Р СР СРЎС“ Р С” Р С•Р С—Р В»Р В°РЎвЂљР Вµ (Р С—Р С•Р Т‘Р Т‘Р ВµРЎР‚Р В¶Р С”Р В° РЎвЂЎР В°РЎРѓРЎвЂљР С‘РЎвЂЎР Р…Р С•Р в„– Р С•Р С—Р В»Р В°РЎвЂљРЎвЂ№)
 * 5. Р СџР В»Р В°Р С–Р С‘Р Р… РЎРѓР С•Р В·Р Т‘Р В°РЎвЂРЎвЂљ Р С—Р В»Р В°РЎвЂљРЎвЂР В¶ Р С‘ Р С—Р С•Р В»РЎС“РЎвЂЎР В°Р ВµРЎвЂљ qrCodeData
 * 6. QR Р С•РЎвЂљР С•Р В±РЎР‚Р В°Р В¶Р В°Р ВµРЎвЂљРЎРѓРЎРЏ Р Р…Р В° Р Т‘Р С‘РЎРѓР С—Р В»Р ВµР Вµ Р С—Р С•Р С”РЎС“Р С—Р В°РЎвЂљР ВµР В»РЎРЏ
 * 7. Р СџР С•Р С”РЎС“Р С—Р В°РЎвЂљР ВµР В»РЎРЉ РЎРѓР С”Р В°Р Р…Р С‘РЎР‚РЎС“Р ВµРЎвЂљ Р С‘ Р С•Р С—Р В»Р В°РЎвЂЎР С‘Р Р†Р В°Р ВµРЎвЂљ
 * 8. Р СџР В»Р В°Р С–Р С‘Р Р… Р С•Р С—РЎР‚Р В°РЎв‚¬Р С‘Р Р†Р В°Р ВµРЎвЂљ РЎРѓРЎвЂљР В°РЎвЂљРЎС“РЎРѓ Р Т‘Р С• paid/completed/failed
 * 9. Р В§Р ВµР С” РЎвЂћР С‘РЎРѓР С”Р В°Р В»Р С‘Р В·Р С‘РЎР‚РЎС“Р ВµРЎвЂљРЎРѓРЎРЏ
 * 10. Р СџР В»Р В°Р С–Р С‘Р Р… Р С•РЎвЂљР С—РЎР‚Р В°Р Р†Р В»РЎРЏР ВµРЎвЂљ Р С—Р С•Р Т‘РЎвЂљР Р†Р ВµРЎР‚Р В¶Р Т‘Р ВµР Р…Р С‘Р Вµ /complete
 */
@POSPlugin(id = "uz.sbgpay.payment")
public class SbgPayPaymentPlugin implements PaymentPlugin, RefundPreparationPlugin, TransactionalRefundPlugin {

    private static final int DEFAULT_HTTP_TIMEOUT_MS = 30000;

    // ====================
    // Р ВР СњР Р„Р вЂўР С™Р В¦Р ВР В Set API
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
    // Р С™Р С›Р СњР В¤Р ВР вЂњР Р€Р В Р С’Р В¦Р ВР Р‡
    // ====================

    private String baseUrl;
    private String deviceToken;
    private String language;
    private String currency;
    private int ttlSeconds;
    private int pollDelayMs;
    private int pollTimeoutSeconds;
    private boolean sendReceipt;
    private volatile String lastLoggedConfigSnapshot;

    // ====================
    // Р РЋР С›Р РЋР СћР С›Р Р‡Р СњР ВР вЂў Р СџР вЂєР С’Р СћР вЂўР вЂ“Р С’
    // ====================

    private volatile String currentPaymentId;
    private volatile String currentPaymentCode;
    private volatile String currentMethodId;
    private volatile String currentMethodName;
    private volatile ScheduledExecutorService statusPoller;
    private volatile boolean paymentInProgress;
    private volatile boolean qrDisplayed;
    private volatile List<PaymentMethod> cachedMethods;

    // ====================
    // PaymentPlugin INTERFACE
    // ====================

    @Override
    public boolean isAvailable() {
        loadConfiguration(false);

        boolean available = hasRequiredConfiguration();
        if (available) {
            log.debug("[SBGPay] isAvailable=true");
        }
        return available;
    }

    @Override
    public void doPayment(PaymentRequest request) {
        log.info("[SBGPay] ===== PAYMENT START =====");
        log.info("[SBGPay] Shop={}, POS={}, Receipt={}",
            posInfo != null ? posInfo.getShopNumber() : "?",
            posInfo != null ? posInfo.getPOSNumber() : "?",
            request.getReceipt().getNumber());

        loadConfiguration();

        if (!hasRequiredConfiguration()) {
            log.error("[SBGPay] Plugin not available");
            request.getPaymentCallback().paymentNotCompleted();
            return;
        }

        // Р РЋР В±РЎР‚Р С•РЎРѓ РЎРѓР С•РЎРѓРЎвЂљР С•РЎРЏР Р…Р С‘РЎРЏ
        resetState();

        final PaymentCallback callback = request.getPaymentCallback();
        final Receipt receipt = request.getReceipt();
        final BigDecimal amount = receipt.getSurchargeSum();

        log.info("[SBGPay] Amount to pay: {} {}", amount, currency);

        // Р СџР С•Р С”Р В°Р В·РЎвЂ№Р Р†Р В°Р ВµР С РЎРѓР С—Р С‘Р Р…Р Р…Р ВµРЎР‚ Р С‘ Р В·Р В°Р С–РЎР‚РЎС“Р В¶Р В°Р ВµР С Р СР ВµРЎвЂљР С•Р Т‘РЎвЂ№ Р С•Р С—Р В»Р В°РЎвЂљРЎвЂ№
        showSpinner(getString("loading.methods", "Р вЂ”Р В°Р С–РЎР‚РЎС“Р В·Р С”Р В° Р СР ВµРЎвЂљР С•Р Т‘Р С•Р Р† Р С•Р С—Р В»Р В°РЎвЂљРЎвЂ№..."));

        new Thread(() -> {
            try {
                List<PaymentMethod> methods = fetchPaymentMethods();

                if (methods.isEmpty()) {
                    SwingUtilities.invokeLater(() ->
                        showErrorAndAbort(getString("error.no.methods", "Р СњР ВµРЎвЂљ Р Т‘Р С•РЎРѓРЎвЂљРЎС“Р С—Р Р…РЎвЂ№РЎвЂ¦ Р СР ВµРЎвЂљР С•Р Т‘Р С•Р Р† Р С•Р С—Р В»Р В°РЎвЂљРЎвЂ№"), callback));
                    return;
                }

                log.info("[SBGPay] Loaded {} payment methods", methods.size());
                SwingUtilities.invokeLater(() ->
                    showMethodSelectionForm(methods, callback, receipt, amount));

            } catch (Exception e) {
                log.error("[SBGPay] Failed to load payment methods", e);
                SwingUtilities.invokeLater(() ->
                    showErrorAndAbort(resolveErrorMessage(
                        e,
                        "error.load.methods",
                        "Р С›РЎв‚¬Р С‘Р В±Р С”Р В° Р В·Р В°Р С–РЎР‚РЎС“Р В·Р С”Р С‘ Р СР ВµРЎвЂљР С•Р Т‘Р С•Р Р†: "),
                        callback));
            }
        }, "sbgpay-load-methods").start();
    }

    @Override
    public void doPaymentCancel(CancelRequest request) {
        loadConfiguration();

        stopStatusPolling();
        clearCustomerDisplay();

        Payment paymentToCancel = request.getPayment();
        String paymentId = null;
        if (paymentToCancel != null) {
            paymentId = extractPaymentIdFromData(paymentToCancel.getData());
        }
        if (!hasText(paymentId)) {
            paymentId = currentPaymentId;
        }

        log.info("[SBGPay] Payment cancel requested, paymentId={}", paymentId);

        if (!hasText(paymentId)) {
            log.warn("[SBGPay] Cancel aborted: source paymentId not found");
            request.getPaymentCallback().paymentNotCompleted();
            return;
        }

        final String paymentIdToCancel = paymentId.trim();
        final Payment paymentForCallback = paymentToCancel;
        new Thread(() -> {
            try {
                cancelOrReversePaymentOnServer(paymentIdToCancel);

                SwingUtilities.invokeLater(() -> {
                    try {
                        if (paymentForCallback != null) {
                            request.getPaymentCallback().paymentCompleted(paymentForCallback);
                        } else {
                            request.getPaymentCallback().paymentNotCompleted();
                        }
                    } catch (InvalidPaymentException e) {
                        log.error("[SBGPay] Cancel callback rejected by POS", e);
                        request.getPaymentCallback().paymentNotCompleted();
                    }
                });
            } catch (Exception e) {
                log.warn("[SBGPay] Cancel request failed for {}: {}", paymentIdToCancel, e.getMessage());
                SwingUtilities.invokeLater(() -> request.getPaymentCallback().paymentNotCompleted());
            }
        }, "sbgpay-cancel").start();
    }

    @Override
    public void doRefund(RefundRequest request) {
        log.info("[SBGPay] ===== REFUND START =====");

        loadConfiguration();

        if (!hasRequiredConfiguration()) {
            log.error("[SBGPay] Refund aborted: plugin not available");
            request.getPaymentCallback().paymentNotCompleted();
            return;
        }

        BigDecimal sumToRefund = request.getSumToRefund();
        if (sumToRefund == null || sumToRefund.compareTo(BigDecimal.ZERO) <= 0) {
            showRefundErrorAndAbort(
                getString("refund.amount.invalid", "Invalid refund amount"),
                request
            );
            return;
        }

        BigDecimal originalPaymentSum = extractOriginalPaymentSum(request);
        if (originalPaymentSum != null && !isFullRefundAmount(sumToRefund, originalPaymentSum)) {
            log.warn("[SBGPay] Partial refund is not supported: requested={}, original={}",
                sumToRefund, originalPaymentSum);
            showRefundErrorAndAbort(
                getString(
                    "refund.partial.not.supported",
                    "Partial refund is not supported: SBG reversal accepts only full refund amount"
                ),
                request
            );
            return;
        }

        String sourcePaymentId = extractSourcePaymentId(request);
        if (sourcePaymentId == null || sourcePaymentId.isEmpty()) {
            showRefundErrorAndAbort(
                getString("refund.source.missing", "Source paymentId was not found for refund"),
                request
            );
            return;
        }

        showSpinner(getString("refund.creating", "Creating refund..."));
        showCustomerText(getString("refund.creating", "Creating refund..."));

        final String sourcePaymentIdFinal = sourcePaymentId;
        new Thread(() -> {
            try {
                RefundResponse initialResponse = reversePaymentOnServer(sourcePaymentIdFinal);
                String initialStatus = initialResponse.status;
                log.info("[SBGPay] Reversal accepted: sourcePaymentId={}, status={}", sourcePaymentIdFinal, initialStatus);

                if (isRefundFailedStatus(initialStatus)) {
                    String detail = initialResponse.errorMessage != null && !initialResponse.errorMessage.isEmpty()
                        ? initialResponse.errorMessage
                        : initialStatus;
                    log.warn("[SBGPay] Reversal failed: sourcePaymentId={}, status={}, error={}",
                        sourcePaymentIdFinal, initialStatus, initialResponse.errorMessage);
                    SwingUtilities.invokeLater(() ->
                        showRefundErrorAndAbort(getString("refund.failed", "Refund failed: ") + detail, request));
                    return;
                }

                RefundResponse terminalResponse;
                if (isRefundSuccessStatus(initialStatus)) {
                    terminalResponse = initialResponse;
                } else {
                    showSpinner(getString("refund.waiting", "Waiting for refund confirmation..."));
                    showCustomerText(getString("refund.waiting", "Waiting for refund confirmation..."));
                    terminalResponse = waitForRefundTerminalStatus(sourcePaymentIdFinal);
                }

                if (isRefundFailedStatus(terminalResponse.status)) {
                    String detail = terminalResponse.errorMessage != null && !terminalResponse.errorMessage.isEmpty()
                        ? terminalResponse.errorMessage
                        : terminalResponse.status;
                    log.warn("[SBGPay] Refund failed: sourcePaymentId={}, status={}, error={}",
                        sourcePaymentIdFinal, terminalResponse.status, terminalResponse.errorMessage);
                    SwingUtilities.invokeLater(() ->
                        showRefundErrorAndAbort(getString("refund.failed", "Refund failed: ") + detail, request));
                    return;
                }

                if (!isRefundSuccessStatus(terminalResponse.status)) {
                    log.warn("[SBGPay] Refund returned unexpected terminal status '{}'", terminalResponse.status);
                    SwingUtilities.invokeLater(() ->
                        showRefundErrorAndAbort(getString("refund.failed", "Refund failed: ") + terminalResponse.status, request));
                    return;
                }

                log.info("[SBGPay] Reversal completed: sourcePaymentId={}, status={}", sourcePaymentIdFinal, terminalResponse.status);
                SwingUtilities.invokeLater(() ->
                    completeRefundFlow(request, sumToRefund, sourcePaymentIdFinal, terminalResponse));

            } catch (Exception e) {
                log.error("[SBGPay] Refund failed", e);
                SwingUtilities.invokeLater(() ->
                    showRefundErrorAndAbort(resolveErrorMessage(
                        e,
                        "refund.create.error",
                        "Refund creation error: "), request));
            }
        }, "sbgpay-refund").start();
    }

    @Override
    public RefundPreparationResult prepareRefund(RefundPreparationRequest request) {
        // Р”Р»СЏ С‚РµРєСѓС‰РµРіРѕ SBG reversal РґРѕРїРѕР»РЅРёС‚РµР»СЊРЅР°СЏ РїРѕРґРіРѕС‚РѕРІРєР°/РіСЂСѓРїРїРёСЂРѕРІРєР° РЅРµ С‚СЂРµР±СѓРµС‚СЃСЏ.
        // Р’РѕР·РІСЂР°С‰Р°РµРј null => РєР°СЃСЃР° РёСЃРїРѕР»СЊР·СѓРµС‚ СЃС‚Р°РЅРґР°СЂС‚РЅС‹Р№ СЃРїРёСЃРѕРє С‚СЂР°РЅР·Р°РєС†РёР№ Рє РІРѕР·РІСЂР°С‚Сѓ.
        return null;
    }

    @Override
    public void doTransactionalRefund(TransactionalRefundRequest request) {
        log.info("[SBGPay] ===== TRANSACTIONAL REFUND START =====");

        loadConfiguration();

        if (!hasRequiredConfiguration()) {
            log.error("[SBGPay] Transactional refund aborted: plugin not available");
            request.getOperationCallback().operationNotCompleted(null);
            return;
        }

        List<PaymentToRefund> paymentsToRefund = request.getPaymentsToRefund();
        if (paymentsToRefund == null || paymentsToRefund.isEmpty()) {
            log.warn("[SBGPay] Transactional refund aborted: no payments to refund");
            request.getOperationCallback().operationNotCompleted(null);
            return;
        }

        BigDecimal totalSumToRefund = request.getSumToRefund();
        if (totalSumToRefund == null || totalSumToRefund.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[SBGPay] Transactional refund aborted: invalid sumToRefund={}", totalSumToRefund);
            request.getOperationCallback().operationNotCompleted(null);
            return;
        }

        new Thread(() -> {
            try {
                List<String> sourcePaymentIds = new ArrayList<>();
                List<String> refundIds = new ArrayList<>();
                List<String> refundCodes = new ArrayList<>();

                for (PaymentToRefund paymentToRefund : paymentsToRefund) {
                    if (paymentToRefund == null || paymentToRefund.getOriginalPayment() == null) {
                        throw new Exception("Original payment is missing in transactional refund request");
                    }

                    ProcessedPayment originalPayment = paymentToRefund.getOriginalPayment();
                    BigDecimal originalPaymentSum = originalPayment.getSum();
                    BigDecimal requestedSum = paymentToRefund.getSumToRefund();

                    if (requestedSum == null || requestedSum.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new Exception(getString("refund.amount.invalid", "Invalid refund amount"));
                    }
                    if (originalPaymentSum != null && !isFullRefundAmount(requestedSum, originalPaymentSum)) {
                        throw new Exception(getString(
                            "refund.partial.not.supported",
                            "Partial refund is not supported: SBG reversal accepts only full refund amount"));
                    }

                    String sourcePaymentId = extractPaymentIdFromData(originalPayment.getData());
                    if (!hasText(sourcePaymentId)) {
                        throw new Exception(getString(
                            "refund.source.missing",
                            "Source paymentId was not found for refund"));
                    }

                    sourcePaymentId = sourcePaymentId.trim();
                    RefundResponse terminalResponse = reverseAndWaitForTerminalStatus(sourcePaymentId);

                    sourcePaymentIds.add(sourcePaymentId);
                    if (hasText(terminalResponse.refundId)) {
                        refundIds.add(terminalResponse.refundId.trim());
                    }
                    if (hasText(terminalResponse.refundCode)) {
                        refundCodes.add(terminalResponse.refundCode.trim());
                    }
                }

                PaymentResultData resultData = new PaymentResultData();
                resultData.getData().put("sbgpay.sourcePaymentId", String.join(",", sourcePaymentIds));
                resultData.getData().put("sbgpay.refundId", String.join(",", refundIds));
                resultData.getData().put("sbgpay.refundCode", String.join(",", refundCodes));
                resultData.getData().put("sbgpay.refundStatus", "refunded");

                StringBuilder slip = new StringBuilder();
                slip.append(getString("refund.success", "Refund completed"));
                slip.append("\n");
                slip.append("Sum: ").append(totalSumToRefund.toPlainString()).append(" ").append(currency);
                slip.append("\n");
                slip.append("sourcePaymentId: ").append(String.join(",", sourcePaymentIds));
                if (!refundIds.isEmpty()) {
                    slip.append("\nrefundId: ").append(String.join(",", refundIds));
                }
                resultData.getSlips().add(slip.toString());

                log.info("[SBGPay] Transactional refund completed: count={}, sum={}",
                    sourcePaymentIds.size(), totalSumToRefund);
                request.getOperationCallback().refundCompleted(new TransactionalRefundResult(resultData));
            } catch (Exception e) {
                log.error("[SBGPay] Transactional refund failed", e);

                PaymentResultData errorData = new PaymentResultData();
                errorData.getData().put("sbgpay.refundStatus", "refund_failed");

                request.getOperationCallback().operationNotCompleted(new TransactionalRefundResult(errorData));
            }
        }, "sbgpay-transactional-refund").start();
    }

    // ====================
    // FISCALIZATION EVENTS
    // ====================

    /**
     * Р вЂ™РЎвЂ№Р В·РЎвЂ№Р Р†Р В°Р ВµРЎвЂљРЎРѓРЎРЏ Р С—Р С•РЎРѓР В»Р Вµ РЎвЂћР С‘РЎРѓР С”Р В°Р В»Р С‘Р В·Р В°РЎвЂ Р С‘Р С‘ РЎвЂЎР ВµР С”Р В°.
     * Р С›РЎвЂљР С—РЎР‚Р В°Р Р†Р В»РЎРЏР ВµРЎвЂљ Р С—Р С•Р Т‘РЎвЂљР Р†Р ВµРЎР‚Р В¶Р Т‘Р ВµР Р…Р С‘Р Вµ Р С•Р С—Р В»Р В°РЎвЂљРЎвЂ№ (/complete) Р Р…Р В° РЎРѓР ВµРЎР‚Р Р†Р ВµРЎР‚ SBG Pay.
     */
    @Override
    public Feedback eventReceiptFiscalized(Receipt receipt, boolean isCancelReceipt) {
        loadConfiguration();

        if (receipt == null) {
            log.debug("[SBGPay] eventReceiptFiscalized: receipt is null");
            return null;
        }

        if (isCancelReceipt) {
            log.debug("[SBGPay] eventReceiptFiscalized: cancel receipt, skipping complete");
            return null;
        }

        log.info("[SBGPay] ===== RECEIPT FISCALIZED =====");

        Collection<ProcessedPayment> payments = receipt.getPayments();
        if (payments == null || payments.isEmpty()) {
            log.debug("[SBGPay] eventReceiptFiscalized: no payments in receipt");
            return null;
        }

        // Р РЋР С•Р В±Р С‘РЎР‚Р В°Р ВµР С Р Р†РЎРѓР Вµ paymentId Р С•РЎвЂљ SBG Pay
        List<String> sbgPaymentIds = new ArrayList<>();
        for (ProcessedPayment payment : payments) {
            Map<String, String> data = payment.getData();
			if (data != null) {
				String paymentId = data.get("sbgpay.paymentId");
				if (paymentId != null && !paymentId.isEmpty()) {
					sbgPaymentIds.add(paymentId);
				}
			}
        }

        if (sbgPaymentIds.isEmpty()) {
            log.debug("[SBGPay] eventReceiptFiscalized: no SBG Pay payments found");
            return null;
        }

        log.info("[SBGPay] Found {} SBG Pay payment(s) to complete", sbgPaymentIds.size());

        // Р СџРЎвЂ№РЎвЂљР В°Р ВµР СРЎРѓРЎРЏ Р С•РЎвЂљР С—РЎР‚Р В°Р Р†Р С‘РЎвЂљРЎРЉ /complete Р Т‘Р В»РЎРЏ Р С”Р В°Р В¶Р Т‘Р С•Р С–Р С• Р С—Р В»Р В°РЎвЂљР ВµР В¶Р В°
        List<String> failedPaymentIds = new ArrayList<>();
        for (String paymentId : sbgPaymentIds) {
            try {
                completePaymentOnServer(paymentId);
                log.info("[SBGPay] Complete successful for paymentId={}", paymentId);
            } catch (Exception e) {
                log.warn("[SBGPay] Complete failed for paymentId={}: {}", paymentId, e.getMessage());
                failedPaymentIds.add(paymentId);
            }
        }

        if (failedPaymentIds.isEmpty()) {
            log.info("[SBGPay] All payments completed successfully");
            return null;
        }

        // Р вЂ™Р С•Р В·Р Р†РЎР‚Р В°РЎвЂ°Р В°Р ВµР С Feedback Р Т‘Р В»РЎРЏ Р С—Р С•Р Р†РЎвЂљР С•РЎР‚Р Р…Р С•Р в„– Р С•РЎвЂљР С—РЎР‚Р В°Р Р†Р С”Р С‘
        log.warn("[SBGPay] {} payment(s) failed to complete, scheduling retry", failedPaymentIds.size());
        String payload = String.join(",", failedPaymentIds);
        return new Feedback(payload);
    }

    /**
     * Р СџР С•Р Р†РЎвЂљР С•РЎР‚Р Р…Р В°РЎРЏ Р С—Р С•Р С—РЎвЂ№РЎвЂљР С”Р В° Р С•РЎвЂљР С—РЎР‚Р В°Р Р†Р С‘РЎвЂљРЎРЉ /complete Р Т‘Р В»РЎРЏ Р С—Р В»Р В°РЎвЂљР ВµР В¶Р ВµР в„–, Р С”Р С•РЎвЂљР С•РЎР‚РЎвЂ№Р Вµ Р Р…Р Вµ РЎС“Р Т‘Р В°Р В»Р С•РЎРѓРЎРЉ Р С—Р С•Р Т‘РЎвЂљР Р†Р ВµРЎР‚Р Т‘Р С‘РЎвЂљРЎРЉ.
     */
    @Override
    public void onRepeatSend(Feedback feedback) throws Exception {
        if (feedback == null || feedback.getPayload() == null || feedback.getPayload().isEmpty()) {
            return;
        }

        loadConfiguration();

        String payload = feedback.getPayload();
        String[] paymentIds = payload.split(",");

        log.info("[SBGPay] onRepeatSend: retrying {} payment(s)", paymentIds.length);

        List<String> stillFailed = new ArrayList<>();
        for (String paymentId : paymentIds) {
            if (paymentId == null || paymentId.trim().isEmpty()) {
                continue;
            }
            paymentId = paymentId.trim();

            try {
                completePaymentOnServer(paymentId);
                log.info("[SBGPay] Complete retry successful for paymentId={}", paymentId);
            } catch (Exception e) {
                log.warn("[SBGPay] Complete retry failed for paymentId={}: {}", paymentId, e.getMessage());
                stillFailed.add(paymentId);
            }
        }

        if (!stillFailed.isEmpty()) {
            // Р С›Р В±Р Р…Р С•Р Р†Р В»РЎРЏР ВµР С payload РЎвЂљР С•Р В»РЎРЉР С”Р С• РЎРѓ Р Р…Р ВµРЎС“Р Т‘Р В°Р Р†РЎв‚¬Р С‘Р СР С‘РЎРѓРЎРЏ
            feedback.setPayload(String.join(",", stillFailed));
            throw new Exception("Failed to complete " + stillFailed.size() + " payment(s): " + stillFailed);
        }

        log.info("[SBGPay] All retried payments completed successfully");
    }

    // ====================
    // PAYMENT FLOW
    // ====================

    /**
     * Р СџР С•Р С”Р В°Р В·РЎвЂ№Р Р†Р В°Р ВµРЎвЂљ РЎвЂћР С•РЎР‚Р СРЎС“ Р Р†РЎвЂ№Р В±Р С•РЎР‚Р В° Р СР ВµРЎвЂљР С•Р Т‘Р В° Р С•Р С—Р В»Р В°РЎвЂљРЎвЂ№
     */
    private void showMethodSelectionForm(List<PaymentMethod> methods,
                                         PaymentCallback callback,
                                         Receipt receipt,
                                         BigDecimal amount) {
        // Р РЋР С•РЎвЂ¦РЎР‚Р В°Р Р…РЎРЏР ВµР С Р Т‘Р В»РЎРЏ Р Р†Р С•Р В·Р СР С•Р В¶Р Р…Р С•РЎРѓРЎвЂљР С‘ Р Р†Р С•Р В·Р Р†РЎР‚Р В°РЎвЂљР В° Р С—РЎР‚Р С‘ Р С•РЎвЂљР СР ВµР Р…Р Вµ Р Р†Р Р†Р С•Р Т‘Р В° РЎРѓРЎС“Р СР СРЎвЂ№
        this.cachedMethods = methods;

        // Р В¤Р С•РЎР‚Р СР С‘РЎР‚РЎС“Р ВµР С Р Т‘Р В°Р Р…Р Р…РЎвЂ№Р Вµ Р Т‘Р В»РЎРЏ РЎвЂљР В°Р В±Р В»Р С‘РЎвЂ РЎвЂ№ Р Р†РЎвЂ№Р В±Р С•РЎР‚Р В°
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
                getString("select.method", "SBG Pay: Р Р†РЎвЂ№Р В±Р ВµРЎР‚Р С‘РЎвЂљР Вµ РЎРѓР С—Р С•РЎРѓР С•Р В± Р С•Р С—Р В»Р В°РЎвЂљРЎвЂ№"),
                items,
                new InputListener() {
                    @Override
                    public void eventInputComplete(String selectedMethodId) {
                        log.info("[SBGPay] Selected method: {}", selectedMethodId);

                        PaymentMethod selected = findMethodById(methods, selectedMethodId);
                        if (selected == null) {
                            showErrorAndAbort(getString("error.method.not.found", "Р СљР ВµРЎвЂљР С•Р Т‘ Р Р…Р Вµ Р Р…Р В°Р в„–Р Т‘Р ВµР Р…"), callback);
                            return;
                        }

                        // Р РЋР С•РЎвЂ¦РЎР‚Р В°Р Р…РЎРЏР ВµР С Р Р†РЎвЂ№Р В±РЎР‚Р В°Р Р…Р Р…РЎвЂ№Р в„– Р СР ВµРЎвЂљР С•Р Т‘
                        currentMethodId = selected.methodId;
                        currentMethodName = selected.name;

                        // Р СџР С•Р С”Р В°Р В·РЎвЂ№Р Р†Р В°Р ВµР С РЎвЂћР С•РЎР‚Р СРЎС“ Р Р†Р Р†Р С•Р Т‘Р В° РЎРѓРЎС“Р СР СРЎвЂ№
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
     * Р СџР С•Р С”Р В°Р В·РЎвЂ№Р Р†Р В°Р ВµРЎвЂљ РЎвЂћР С•РЎР‚Р СРЎС“ Р Р†Р Р†Р С•Р Т‘Р В° РЎРѓРЎС“Р СР СРЎвЂ№ Р С” Р С•Р С—Р В»Р В°РЎвЂљР Вµ
     */
    private void showAmountInputForm(PaymentMethod method,
                                      PaymentCallback callback,
                                      Receipt receipt,
                                      BigDecimal defaultSum) {

        String title = method.name != null && !method.name.isEmpty()
            ? method.name
            : getString("payment.name", "SBG Pay");

        SumToPayFormParameters parameters = new SumToPayFormParameters(title, receipt);
        parameters.setInputHint(getString("enter.sum.to.pay", "Р вЂ™Р Р†Р ВµР Т‘Р С‘РЎвЂљР Вµ РЎРѓРЎС“Р СР СРЎС“ Р С” Р С•Р С—Р В»Р В°РЎвЂљР Вµ"));
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

                    // Р вЂ™Р В°Р В»Р С‘Р Т‘Р В°РЎвЂ Р С‘РЎРЏ РЎРѓРЎС“Р СР СРЎвЂ№ Р С—Р С• Р В»Р С‘Р СР С‘РЎвЂљР В°Р С Р СР ВµРЎвЂљР С•Р Т‘Р В°
                    String validationError = validateAmountForMethod(enteredAmount, method);
                    if (validationError != null) {
                        showAmountValidationError(validationError, method, callback, receipt, defaultSum);
                        return;
                    }

                    // Р РЋР С•Р В·Р Т‘Р В°РЎвЂР С Р С—Р В»Р В°РЎвЂљРЎвЂР В¶ Р Р…Р В° Р Р†Р Р†Р ВµР Т‘РЎвЂР Р…Р Р…РЎС“РЎР‹ РЎРѓРЎС“Р СР СРЎС“
                    createPayment(method, callback, receipt, enteredAmount);
                }
            });
        } catch (IncorrectStateException e) {
            log.error("[SBGPay] Failed to show amount input form", e);
            callback.paymentNotCompleted();
        }
    }

    /**
     * Р вЂ™Р В°Р В»Р С‘Р Т‘Р В°РЎвЂ Р С‘РЎРЏ РЎРѓРЎС“Р СР СРЎвЂ№ Р С—Р С• Р В»Р С‘Р СР С‘РЎвЂљР В°Р С Р СР ВµРЎвЂљР С•Р Т‘Р В° Р С•Р С—Р В»Р В°РЎвЂљРЎвЂ№
     * @return null Р ВµРЎРѓР В»Р С‘ OK, Р С‘Р Р…Р В°РЎвЂЎР Вµ РЎвЂљР ВµР С”РЎРѓРЎвЂљ Р С•РЎв‚¬Р С‘Р В±Р С”Р С‘
     */
    private String validateAmountForMethod(BigDecimal amount, PaymentMethod method) {
        long amountMinor = toMinorUnits(amount);

        if (method.minAmount > 0 && amountMinor < method.minAmount) {
            return getString("error.amount.too.small", "Р РЋРЎС“Р СР СР В° Р СР ВµР Р…РЎРЉРЎв‚¬Р Вµ Р СР С‘Р Р…Р С‘Р СР В°Р В»РЎРЉР Р…Р С•Р в„–: ")
                + fromMinorUnits(method.minAmount) + " " + currency;
        }

        if (method.maxAmount > 0 && amountMinor > method.maxAmount) {
            return getString("error.amount.too.large", "Р РЋРЎС“Р СР СР В° Р В±Р С•Р В»РЎРЉРЎв‚¬Р Вµ Р СР В°Р С”РЎРѓР С‘Р СР В°Р В»РЎРЉР Р…Р С•Р в„–: ")
                + fromMinorUnits(method.maxAmount) + " " + currency;
        }

        return null;
    }

    /**
     * Р СџР С•Р С”Р В°Р В·РЎвЂ№Р Р†Р В°Р ВµРЎвЂљ Р С•РЎв‚¬Р С‘Р В±Р С”РЎС“ Р Р†Р В°Р В»Р С‘Р Т‘Р В°РЎвЂ Р С‘Р С‘ РЎРѓРЎС“Р СР СРЎвЂ№ Р С‘ Р Р†Р С•Р В·Р Р†РЎР‚Р В°РЎвЂ°Р В°Р ВµРЎвЂљ Р С” Р Р†Р Р†Р С•Р Т‘РЎС“ РЎРѓРЎС“Р СР СРЎвЂ№
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
     * Р РЋР С•Р В·Р Т‘Р В°РЎвЂРЎвЂљ Р С—Р В»Р В°РЎвЂљРЎвЂР В¶ РЎвЂЎР ВµРЎР‚Р ВµР В· API Р С‘ Р В·Р В°Р С—РЎС“РЎРѓР С”Р В°Р ВµРЎвЂљ Р С•РЎвЂљР С•Р В±РЎР‚Р В°Р В¶Р ВµР Р…Р С‘Р Вµ QR + polling
     */
    private void createPayment(PaymentMethod method,
                               PaymentCallback callback,
                               Receipt receipt,
                               BigDecimal amount) {

        paymentInProgress = true;
        currentMethodId = method.methodId;
        currentMethodName = method.name;

        showSpinner(getString("creating.payment", "Р РЋР С•Р В·Р Т‘Р В°Р Р…Р С‘Р Вµ Р С—Р В»Р В°РЎвЂљР ВµР В¶Р В°..."));
        showCustomerText(currentMethodName + "\n" + getString("preparing", "Р СџР С•Р Т‘Р С–Р С•РЎвЂљР С•Р Р†Р С”Р В°..."));

        new Thread(() -> {
            try {
                CreatePaymentResponse response = createPaymentOnServer(method, receipt, amount);

                currentPaymentId = response.paymentId;
                currentPaymentCode = response.paymentCode;
                final String initialQrData = response.qrCodeData;

                log.info("[SBGPay] Payment created: id={}, code={}, status={}",
                    currentPaymentId, currentPaymentCode, response.status);

                SwingUtilities.invokeLater(() -> {
                    showWaitingForPaymentSpinner();
                    if (!qrDisplayed && initialQrData != null && !initialQrData.isEmpty()) {
                        qrDisplayed = true;
                        log.info("[SBGPay] Received QR in create response, showing on customer display");
                        showQrOnCustomerDisplay(amount, initialQrData);
                    }
                });

                startStatusPolling(callback, amount);

            } catch (Exception e) {
                log.error("[SBGPay] Failed to create payment", e);
                SwingUtilities.invokeLater(() ->
                    showErrorAndAbort(resolveErrorMessage(
                        e,
                        "error.create.payment",
                        "Р С›РЎв‚¬Р С‘Р В±Р С”Р В° РЎРѓР С•Р В·Р Т‘Р В°Р Р…Р С‘РЎРЏ Р С—Р В»Р В°РЎвЂљР ВµР В¶Р В°: "),
                        callback));
            }
        }, "sbgpay-create-payment").start();
    }

    /**
     * Р вЂ”Р В°Р С—РЎС“РЎРѓР С”Р В°Р ВµРЎвЂљ Р С—Р ВµРЎР‚Р С‘Р С•Р Т‘Р С‘РЎвЂЎР ВµРЎРѓР С”Р С‘Р в„– Р С•Р С—РЎР‚Р С•РЎРѓ РЎРѓРЎвЂљР В°РЎвЂљРЎС“РЎРѓР В° Р С—Р В»Р В°РЎвЂљР ВµР В¶Р В°
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
                long remainingMs = timeoutMs - elapsed;

                if (remainingMs <= 0) {
                    log.warn("[SBGPay] Payment timeout after {}ms", elapsed);
                    stopStatusPolling();
                    SwingUtilities.invokeLater(() ->
                        showErrorAndAbort(getString("error.timeout", "Р вЂ™РЎР‚Р ВµР СРЎРЏ Р С•Р В¶Р С‘Р Т‘Р В°Р Р…Р С‘РЎРЏ Р С•Р С—Р В»Р В°РЎвЂљРЎвЂ№ Р С‘РЎРѓРЎвЂљР ВµР С”Р В»Р С•"), callback));
                    return;
                }

                int statusRequestTimeoutMs = calculateStatusRequestTimeoutMs(remainingMs);
                PaymentStatus status = fetchPaymentStatus(currentPaymentId, statusRequestTimeoutMs);
                String qrData = getQrData(status);
                log.debug("[SBGPay] Status poll: status={}, hasQrData={}, elapsed={}ms, remaining={}ms, requestTimeout={}ms",
                    status.status, qrData != null && !qrData.isEmpty(), elapsed, remainingMs, statusRequestTimeoutMs);

                if (!qrDisplayed && qrData != null && !qrData.isEmpty()) {
                    qrDisplayed = true;
                    log.info("[SBGPay] Received QR in status response, showing on customer display");
                    final String qrDataToDisplay = qrData;
                    SwingUtilities.invokeLater(() -> showQrOnCustomerDisplay(amount, qrDataToDisplay));
                }

                if (isSuccessStatus(status.status)) {
                    log.info("[SBGPay] Payment completed successfully");
                    stopStatusPolling();
                    SwingUtilities.invokeLater(() -> completePaymentFlow(callback, amount, status));

                } else if (isFailedStatus(status.status)) {
                    String errorDetail = status.errorMessage != null && !status.errorMessage.isEmpty()
                        ? status.errorMessage
                        : status.status;
                    log.warn("[SBGPay] Payment failed: status={}, error={}", status.status, status.errorMessage);
                    stopStatusPolling();
                    SwingUtilities.invokeLater(() ->
                        showErrorAndAbort(getString("error.payment.failed", "Р С›Р С—Р В»Р В°РЎвЂљР В° Р Р…Р Вµ Р Р†РЎвЂ№Р С—Р С•Р В»Р Р…Р ВµР Р…Р В°: ") + errorDetail, callback));
                }

            } catch (Exception e) {
                long elapsedAfterError = System.currentTimeMillis() - startTime;
                long remainingAfterError = Math.max(0L, timeoutMs - elapsedAfterError);
                int requestTimeoutMs = calculateStatusRequestTimeoutMs(Math.max(1L, remainingAfterError));

                if (isRequestTimeoutException(e)) {
                    log.warn("[SBGPay] Status poll request timeout: elapsed={}ms, remaining={}ms, requestTimeout={}ms, error={}",
                        elapsedAfterError, remainingAfterError, requestTimeoutMs, extractErrorDetails(e));

                    if (elapsedAfterError >= timeoutMs) {
                        log.warn("[SBGPay] Payment timeout after polling timeouts ({}ms)", elapsedAfterError);
                        stopStatusPolling();
                        SwingUtilities.invokeLater(() ->
                            showErrorAndAbort(getString("error.timeout", "Время ожидания оплаты истекло"), callback));
                    }
                    return;
                }

                if (isCommunicationException(e)) {
                    log.error("[SBGPay] Status polling communication error", e);
                    stopStatusPolling();
                    SwingUtilities.invokeLater(() ->
                        showErrorAndAbort(
                            getString("error.communication",
                                "Нет связи с процессингом. Проверьте сеть и повторите операцию."),
                            callback));
                    return;
                }

                log.error("[SBGPay] Status polling error", e);
                if (elapsedAfterError >= timeoutMs) {
                    log.warn("[SBGPay] Payment timeout after polling error ({}ms)", elapsedAfterError);
                    stopStatusPolling();
                    SwingUtilities.invokeLater(() ->
                        showErrorAndAbort(getString("error.timeout", "Время ожидания оплаты истекло"), callback));
                }
            }
        }, pollDelayMs, pollDelayMs, TimeUnit.MILLISECONDS);
    }

    private void stopStatusPolling() {
        if (statusPoller != null) {
            try {
                statusPoller.shutdownNow();
            } catch (RuntimeException e) {
                log.warn("[SBGPay] Error while stopping status poller", e);
            }
            statusPoller = null;
            log.debug("[SBGPay] Status polling stopped");
        }
    }

    /**
     * Р вЂ”Р В°Р Р†Р ВµРЎР‚РЎв‚¬Р В°Р ВµРЎвЂљ Р С—РЎР‚Р С•РЎвЂ Р ВµРЎРѓРЎРѓ Р С•Р С—Р В»Р В°РЎвЂљРЎвЂ№ РІР‚вЂќ Р С—Р ВµРЎР‚Р ВµР Т‘Р В°РЎвЂРЎвЂљ Р Т‘Р В°Р Р…Р Р…РЎвЂ№Р Вµ Р С”Р В°РЎРѓРЎРѓР Вµ.
     * Р СџР С•Р Т‘РЎвЂљР Р†Р ВµРЎР‚Р В¶Р Т‘Р ВµР Р…Р С‘Р Вµ /complete Р В±РЎС“Р Т‘Р ВµРЎвЂљ Р С•РЎвЂљР С—РЎР‚Р В°Р Р†Р В»Р ВµР Р…Р С• Р Р† eventReceiptFiscalized Р С—Р С•РЎРѓР В»Р Вµ РЎвЂћР С‘РЎРѓР С”Р В°Р В»Р С‘Р В·Р В°РЎвЂ Р С‘Р С‘.
     */
    private void completePaymentFlow(PaymentCallback callback, BigDecimal amount, PaymentStatus status) {
        paymentInProgress = false;

        String title = currentMethodName != null && !currentMethodName.isEmpty()
            ? currentMethodName
            : "SBG Pay";
        String completedPaymentId = resolveCompletedPaymentId(status);
        String completedPaymentCode = resolveCompletedPaymentCode(status);

        clearCustomerDisplay();
        showCustomerText(title + "\n" + getString("payment.success", "Р С›Р С—Р В»Р В°РЎвЂљР В° РЎС“РЎРѓР С—Р ВµРЎв‚¬Р Р…Р В°!"));

        Payment payment = new Payment();
        payment.setSum(amount);

        // Р РЋР С•РЎвЂ¦РЎР‚Р В°Р Р…РЎРЏР ВµР С Р Т‘Р В°Р Р…Р Р…РЎвЂ№Р Вµ Р Т‘Р В»РЎРЏ Р С‘РЎРѓР С—Р С•Р В»РЎРЉР В·Р С•Р Р†Р В°Р Р…Р С‘РЎРЏ Р Р† eventReceiptFiscalized
        payment.getData().put("sbgpay.paymentId", nullToEmpty(completedPaymentId));
        payment.getData().put("sbgpay.paymentCode", nullToEmpty(completedPaymentCode));
        payment.getData().put("sbgpay.methodId", nullToEmpty(currentMethodId));
        payment.getData().put("sbgpay.methodName", nullToEmpty(currentMethodName));
        payment.getData().put("sbgpay.status", nullToEmpty(status.status));

        log.info("[SBGPay] ===== PAYMENT FLOW COMPLETED =====");
        log.info("[SBGPay] paymentId={}, method={}, status={}, amount={}",
            completedPaymentId, currentMethodName, status.status, amount);
        log.info("[SBGPay] Stored paymentId in receipt data: key='sbgpay.paymentId', value={}", completedPaymentId);

        try {
            callback.paymentCompleted(payment);
        } catch (InvalidPaymentException e) {
            log.error("[SBGPay] Payment rejected by POS", e);
            callback.paymentNotCompleted();
        }
    }

    private void completeRefundFlow(RefundRequest request,
                                    BigDecimal sumToRefund,
                                    String sourcePaymentId,
                                    RefundResponse refundResponse) {
        clearCustomerDisplay();
        showCustomerText(getString("refund.success", "Р вЂ™Р С•Р В·Р Р†РЎР‚Р В°РЎвЂљ Р Р†РЎвЂ№Р С—Р С•Р В»Р Р…Р ВµР Р…"));

        Payment payment = new Payment();
        payment.setSum(sumToRefund);
        payment.getData().put("sbgpay.sourcePaymentId", nullToEmpty(sourcePaymentId));
        payment.getData().put("sbgpay.refundId", nullToEmpty(refundResponse.refundId));
        payment.getData().put("sbgpay.refundCode", nullToEmpty(refundResponse.refundCode));
        payment.getData().put("sbgpay.refundStatus", nullToEmpty(refundResponse.status));

        log.info("[SBGPay] ===== REFUND COMPLETED =====");
        log.info("[SBGPay] sourcePaymentId={}, refundId={}, status={}, amount={}",
            sourcePaymentId, refundResponse.refundId, refundResponse.status, sumToRefund);

        try {
            request.getPaymentCallback().paymentCompleted(payment);
        } catch (InvalidPaymentException e) {
            log.error("[SBGPay] Refund payment rejected by POS", e);
            request.getPaymentCallback().paymentNotCompleted();
        }
    }
    private void showRefundErrorAndAbort(String message, RefundRequest request) {
        clearCustomerDisplay();
        showCustomerText(message);

        log.warn("[SBGPay] Refund aborted: {}", message);

        try {
            uiForms.showMessageForm(message, new ConfirmListener() {
                @Override
                public void eventConfirmed() {
                    request.getPaymentCallback().paymentNotCompleted();
                }
            });
        } catch (IncorrectStateException e) {
            request.getPaymentCallback().paymentNotCompleted();
        }
    }

    private String extractSourcePaymentId(RefundRequest request) {
        try {
            if (request.getOriginalPayment() != null && request.getOriginalPayment().getData() != null) {
                String paymentId = extractPaymentIdFromData(request.getOriginalPayment().getData());
                if (paymentId != null && !paymentId.trim().isEmpty()) {
                    return paymentId.trim();
                }
            }
        } catch (Exception e) {
            log.debug("[SBGPay] Failed to read originalPayment data for refund", e);
        }

        try {
            Receipt originalReceipt = request.getOriginalReceipt();
            if (originalReceipt != null && originalReceipt.getPayments() != null) {
                for (ProcessedPayment processedPayment : originalReceipt.getPayments()) {
                    if (processedPayment.getData() == null) {
                        continue;
                    }
                    String paymentId = extractPaymentIdFromData(processedPayment.getData());
                    if (paymentId != null && !paymentId.trim().isEmpty()) {
                        return paymentId.trim();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[SBGPay] Failed to read originalReceipt data for refund", e);
        }

        return null;
    }

    private BigDecimal extractOriginalPaymentSum(RefundRequest request) {
        try {
            if (request.getOriginalPayment() != null) {
                BigDecimal sum = request.getOriginalPayment().getSum();
                if (sum != null && sum.compareTo(BigDecimal.ZERO) > 0) {
                    return sum;
                }
            }
        } catch (Exception e) {
            log.debug("[SBGPay] Failed to read originalPayment sum for refund", e);
        }
        return null;
    }

    private boolean isFullRefundAmount(BigDecimal requestedSum, BigDecimal originalSum) {
        if (requestedSum == null || originalSum == null) {
            return false;
        }
        return requestedSum.compareTo(originalSum) == 0;
    }

    private String resolveCompletedPaymentId(PaymentStatus status) {
        String statusPaymentId = status != null ? status.paymentId : null;
        if (hasText(statusPaymentId)) {
            if (hasText(currentPaymentId) && !currentPaymentId.equals(statusPaymentId)) {
                logWarnSafe("[SBGPay] paymentId mismatch: create='{}', status='{}' - storing status paymentId",
                    currentPaymentId, statusPaymentId);
            }
            return statusPaymentId;
        }
        return currentPaymentId;
    }

    private String resolveCompletedPaymentCode(PaymentStatus status) {
        String statusPaymentCode = status != null ? status.paymentCode : null;
        if (hasText(statusPaymentCode)) {
            return statusPaymentCode;
        }
        return currentPaymentCode;
    }

    private String extractPaymentIdFromData(Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        String paymentId = data.get("sbgpay.paymentId");
        if (hasText(paymentId)) {
            return paymentId.trim();
        }

        paymentId = data.get("paymentId");
        if (hasText(paymentId)) {
            if (log != null) {
                log.debug("[SBGPay] Using fallback paymentId key from receipt data: 'paymentId'");
            }
            return paymentId.trim();
        }

        return null;
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
                log.debug("[SBGPay] Parsed method: id={}, name={}, kind={}, currency={}, providerCode={}, description={}, iconUrl={}",
                    m.methodId, m.name, m.kind, m.currency, m.providerCode, m.description, m.iconUrl);
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
    private PaymentStatus fetchPaymentStatus(String paymentId, int timeoutMs) throws Exception {
        String url = baseUrl + "/api/v1/payments/" + urlEncode(paymentId) + "/status";

        JsonNode response = httpGet(url, timeoutMs);

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
     * POST /api/v1/payments/{id}/complete
     */
    private void completePaymentOnServer(String paymentId) throws Exception {
        PaymentStatus currentStatus = fetchPaymentStatus(paymentId, DEFAULT_HTTP_TIMEOUT_MS);
        String status = currentStatus.status;

        if ("completed".equalsIgnoreCase(status)) {
            log.info("[SBGPay] Complete skipped for paymentId={}: status already completed", paymentId);
            return;
        }
        if (!"paid".equalsIgnoreCase(status)) {
            throw new Exception("Cannot complete payment in status '" + status + "'");
        }

        String url = baseUrl + "/api/v1/payments/" + urlEncode(paymentId) + "/complete";
        try {
            httpPost(url, new LinkedHashMap<>(), UUID.randomUUID().toString());
            log.debug("[SBGPay] Complete request sent for paymentId={}", paymentId);
        } catch (HttpStatusException e) {
            if (isAlreadyCompletedConflict(e)) {
                log.info("[SBGPay] Complete is idempotent for paymentId={}: status already completed", paymentId);
                return;
            }
            throw e;
        }
    }

    /**
     * POST /api/v1/payments/{id}/cancel
     */
    private void cancelPaymentOnServer(String paymentId) throws Exception {
        String url = baseUrl + "/api/v1/payments/" + urlEncode(paymentId) + "/cancel";
        httpPost(url, new LinkedHashMap<>(), UUID.randomUUID().toString());
        log.debug("[SBGPay] Cancel request sent for paymentId={}", paymentId);
    }

    private void cancelOrReversePaymentOnServer(String paymentId) throws Exception {
        PaymentStatus statusBeforeCancel = null;
        try {
            statusBeforeCancel = fetchPaymentStatus(paymentId, DEFAULT_HTTP_TIMEOUT_MS);
        } catch (Exception e) {
            log.warn("[SBGPay] Failed to fetch status before cancel for paymentId={}: {}, trying /cancel directly",
                paymentId, e.getMessage());
        }

        String currentStatus = statusBeforeCancel != null ? statusBeforeCancel.status : null;
        log.info("[SBGPay] Cancel route: paymentId={}, currentStatus={}", paymentId, currentStatus);

        if (isSuccessStatus(currentStatus)) {
            RefundResponse terminalResponse = reverseAndWaitForTerminalStatus(paymentId);
            log.info("[SBGPay] Cancel routed to reversal: paymentId={}, status={}",
                paymentId, terminalResponse.status);
            return;
        }

        if (isRefundSuccessStatus(currentStatus) || isReversedStatus(currentStatus)) {
            log.info("[SBGPay] Cancel skipped: paymentId={} already refunded/reversed (status={})",
                paymentId, currentStatus);
            return;
        }

        if (isCancelledStatus(currentStatus)) {
            log.info("[SBGPay] Cancel skipped: paymentId={} already cancelled (status={})",
                paymentId, currentStatus);
            return;
        }

        if (isFailedStatus(currentStatus)) {
            log.info("[SBGPay] Cancel skipped: paymentId={} already terminal failed (status={})",
                paymentId, currentStatus);
            return;
        }

        try {
            cancelPaymentOnServer(paymentId);
            log.info("[SBGPay] Payment cancelled on server: {}", paymentId);
        } catch (HttpStatusException e) {
            if (isCancelConflictForCompleted(e)) {
                RefundResponse terminalResponse = reverseAndWaitForTerminalStatus(paymentId);
                log.info("[SBGPay] Cancel conflict resolved via reversal: paymentId={}, status={}",
                    paymentId, terminalResponse.status);
                return;
            }

            PaymentStatus statusAfterConflict = null;
            try {
                statusAfterConflict = fetchPaymentStatus(paymentId, DEFAULT_HTTP_TIMEOUT_MS);
            } catch (Exception ignored) {
                // keep original exception context below
            }

            String conflictStatus = statusAfterConflict != null ? statusAfterConflict.status : null;
            if (isSuccessStatus(conflictStatus)) {
                RefundResponse terminalResponse = reverseAndWaitForTerminalStatus(paymentId);
                log.info("[SBGPay] Cancel conflict routed to reversal: paymentId={}, status={}",
                    paymentId, terminalResponse.status);
                return;
            }
            if (isRefundSuccessStatus(conflictStatus) || isReversedStatus(conflictStatus) || isCancelledStatus(conflictStatus) || isFailedStatus(conflictStatus)) {
                log.info("[SBGPay] Cancel conflict treated as idempotent success: paymentId={}, status={}",
                    paymentId, conflictStatus);
                return;
            }

            throw e;
        }
    }

    private RefundResponse reversePaymentOnServer(String sourcePaymentId) throws Exception {
        String url = baseUrl + "/api/v1/payments/" + urlEncode(sourcePaymentId) + "/reversal";

        try {
            JsonNode response = httpPost(url, null, UUID.randomUUID().toString(), false);
            log.debug("[SBGPay] Reversal response: {}", response.toString());
            return mapReversalResponse(response, sourcePaymentId, "refunding");
        } catch (HttpStatusException e) {
            if (isAlreadyReversedConflict(e)) {
                log.info("[SBGPay] Reversal is idempotent for paymentId={}: status already reversed", sourcePaymentId);
                return mapReversalResponse(null, sourcePaymentId, "refunded");
            }
            throw e;
        }
    }

    private RefundResponse mapReversalResponse(JsonNode response,
                                               String sourcePaymentId,
                                               String fallbackStatus) {
        RefundResponse refund = new RefundResponse();

        refund.refundId = getText(response, "reversalId");
        if (refund.refundId == null) refund.refundId = getText(response, "refundId");
        if (refund.refundId == null) refund.refundId = getText(response, "id");

        refund.refundCode = getText(response, "reversalCode");
        if (refund.refundCode == null) refund.refundCode = getText(response, "refundCode");
        if (refund.refundCode == null) refund.refundCode = getText(response, "code");

        refund.status = getText(response, "status");
        if (refund.status == null) refund.status = getText(response, "reversalStatus");
        if (refund.status == null) refund.status = fallbackStatus;
        if (refund.status == null) refund.status = "refunding";

        refund.errorMessage = getText(response, "errorMessage");
        if (refund.errorMessage == null) refund.errorMessage = getText(response, "error");
        if (refund.errorMessage == null) refund.errorMessage = getText(response, "message");

        if (refund.refundId == null) {
            refund.refundId = sourcePaymentId;
        }

        return refund;
    }

    private RefundResponse waitForRefundTerminalStatus(String paymentId) throws Exception {
        long startTime = System.currentTimeMillis();
        long timeoutMs = pollTimeoutSeconds * 1000L;
        String lastStatus = null;

        while (true) {
            long elapsed = System.currentTimeMillis() - startTime;
            long remainingMs = timeoutMs - elapsed;
            if (remainingMs <= 0) {
                throw new Exception("Refund timeout: lastStatus=" + (lastStatus != null ? lastStatus : "unknown"));
            }

            int requestTimeoutMs = calculateStatusRequestTimeoutMs(remainingMs);
            PaymentStatus paymentStatus = fetchPaymentStatus(paymentId, requestTimeoutMs);
            lastStatus = paymentStatus.status;

            log.debug("[SBGPay] Refund status poll: paymentId={}, status={}, elapsed={}ms, remaining={}ms, requestTimeout={}ms",
                paymentId, lastStatus, elapsed, remainingMs, requestTimeoutMs);

            if (isRefundSuccessStatus(lastStatus) || isRefundFailedStatus(lastStatus)) {
                RefundResponse terminal = new RefundResponse();
                terminal.refundId = paymentId;
                terminal.status = lastStatus;
                terminal.errorMessage = paymentStatus.errorMessage;
                return terminal;
            }

            try {
                Thread.sleep(Math.max(200, pollDelayMs));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new Exception("Refund polling interrupted");
            }
        }
    }

    private RefundResponse reverseAndWaitForTerminalStatus(String sourcePaymentId) throws Exception {
        RefundResponse initialResponse = reversePaymentOnServer(sourcePaymentId);
        String initialStatus = initialResponse.status;

        log.info("[SBGPay] Reversal accepted: sourcePaymentId={}, status={}", sourcePaymentId, initialStatus);

        if (isRefundFailedStatus(initialStatus)) {
            String detail = initialResponse.errorMessage != null && !initialResponse.errorMessage.isEmpty()
                ? initialResponse.errorMessage
                : initialStatus;
            throw new Exception(detail);
        }

        RefundResponse terminalResponse = isRefundSuccessStatus(initialStatus)
            ? initialResponse
            : waitForRefundTerminalStatus(sourcePaymentId);

        if (isRefundFailedStatus(terminalResponse.status)) {
            String detail = terminalResponse.errorMessage != null && !terminalResponse.errorMessage.isEmpty()
                ? terminalResponse.errorMessage
                : terminalResponse.status;
            throw new Exception(detail);
        }

        if (!isRefundSuccessStatus(terminalResponse.status)) {
            throw new Exception("Unexpected refund terminal status: " + terminalResponse.status);
        }

        return terminalResponse;
    }

    /**
     * РїС—Р…РїС—Р…РїС—Р…РїС—Р…РїС—Р…РїС—Р…РїС—Р…РїС—Р…РїС—Р… РїС—Р…РїС—Р…РїС—Р…РїС—Р…РїС—Р…РїС—Р… receipt РїС—Р…РїС—Р…РїС—Р…РїС—Р…РїС—Р…РїС—Р…РїС—Р…РїС—Р… РїС—Р…РїС—Р…РїС—Р…РїС—Р…РїС—Р…РїС—Р…РїС—Р…РїС—Р…РїС—Р…РїС—Р…РїС—Р…РїС—Р…
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
                itemObj.put("barcode", barcode != null && !barcode.isEmpty() ? barcode : null);

                itemObj.put("price", toMinorUnits(merchandise.getPrice()));
            } else {
                itemObj.put("name", "Unknown");
                itemObj.put("sku", "");
                itemObj.put("barcode", null);
                itemObj.put("price", 0L);
            }

            itemObj.put("qty", item.getQuantity() / 1000.0);
            itemObj.put("total", toMinorUnits(item.getSum()));

            // Р вЂќР С•Р В±Р В°Р Р†Р В»РЎРЏР ВµР С Р ВµР Т‘Р С‘Р Р…Р С‘РЎвЂ РЎС“ Р С‘Р В·Р СР ВµРЎР‚Р ВµР Р…Р С‘РЎРЏ
            String unit = mapMeasureUnit(item);
            itemObj.put("unit", unit);

            items.add(itemObj);
        }

        receiptObj.put("items", items);
        return receiptObj;
    }

    /**
     * Р СљР В°Р С—Р С—Р С‘Р Р…Р С– Р ВµР Т‘Р С‘Р Р…Р С‘РЎвЂ РЎвЂ№ Р С‘Р В·Р СР ВµРЎР‚Р ВµР Р…Р С‘РЎРЏ Р С‘Р В· Set Retail 10 Р Р† РЎвЂћР С•РЎР‚Р СР В°РЎвЂљ SBG Pay
     */
    private String mapMeasureUnit(LineItem item) {
        // Р СџРЎР‚Р С•Р В±РЎС“Р ВµР С Р С—Р С•Р В»РЎС“РЎвЂЎР С‘РЎвЂљРЎРЉ Р С”Р С•Р Т‘ Р С›Р С™Р вЂўР В
        String rcumCode = item.getMeasureRcumCode();
        if (rcumCode != null && !rcumCode.isEmpty()) {
            switch (rcumCode) {
                case "166": return "kg";   // Р С”Р С‘Р В»Р С•Р С–РЎР‚Р В°Р СР С
                case "163": return "g";    // Р С–РЎР‚Р В°Р СР С
                case "112": return "l";    // Р В»Р С‘РЎвЂљРЎР‚
                case "006": return "m";    // Р СР ВµРЎвЂљРЎР‚
                case "796": return "pcs";  // РЎв‚¬РЎвЂљРЎС“Р С”Р С‘
                default: break;
            }
        }

        // Р СџРЎР‚Р С•Р В±РЎС“Р ВµР С Р С—Р С• Р Р…Р В°Р В·Р Р†Р В°Р Р…Р С‘РЎР‹ Р ВµР Т‘Р С‘Р Р…Р С‘РЎвЂ РЎвЂ№ Р С‘Р В·Р СР ВµРЎР‚Р ВµР Р…Р С‘РЎРЏ
        String measureName = item.getMeasureName();
        if (measureName != null && !measureName.isEmpty()) {
            String nameLower = measureName.toLowerCase().trim();

            // Р С™Р С‘Р В»Р С•Р С–РЎР‚Р В°Р СР С
            if (nameLower.equals("Р С”Р С–") || nameLower.equals("Р С”Р С‘Р В»Р С•Р С–РЎР‚Р В°Р СР С") ||
                nameLower.equals("kg") || nameLower.equals("kilogram")) {
                return "kg";
            }

            // Р вЂњРЎР‚Р В°Р СР С
            if (nameLower.equals("Р С–") || nameLower.equals("Р С–РЎР‚Р В°Р СР С") ||
                nameLower.equals("g") || nameLower.equals("gr") || nameLower.equals("gram")) {
                return "g";
            }

            // Р вЂєР С‘РЎвЂљРЎР‚
            if (nameLower.equals("Р В»") || nameLower.equals("Р В»Р С‘РЎвЂљРЎР‚") ||
                nameLower.equals("l") || nameLower.equals("liter") || nameLower.equals("litr")) {
                return "l";
            }

            // Р СљР ВµРЎвЂљРЎР‚
            if (nameLower.equals("Р С") || nameLower.equals("Р СР ВµРЎвЂљРЎР‚") ||
                nameLower.equals("m") || nameLower.equals("meter") || nameLower.equals("metr")) {
                return "m";
            }

            // Р РЃРЎвЂљРЎС“Р С”Р С‘ (РЎР‚РЎС“РЎРѓРЎРѓР С”Р С‘Р в„–, РЎС“Р В·Р В±Р ВµР С”РЎРѓР С”Р С‘Р в„– Р С”Р С‘РЎР‚Р С‘Р В»Р В»Р С‘РЎвЂ Р В°, РЎС“Р В·Р В±Р ВµР С”РЎРѓР С”Р С‘Р в„– Р В»Р В°РЎвЂљР С‘Р Р…Р С‘РЎвЂ Р В°, Р В°Р Р…Р С–Р В»Р С‘Р в„–РЎРѓР С”Р С‘Р в„–)
            if (nameLower.equals("РЎв‚¬РЎвЂљ") || nameLower.equals("РЎв‚¬РЎвЂљРЎС“Р С”Р В°") || nameLower.equals("РЎв‚¬РЎвЂљРЎС“Р С”Р С‘") ||
                nameLower.equals("Р Т‘Р С•Р Р…Р В°") ||                          // РЎС“Р В·Р В±Р ВµР С”РЎРѓР С”Р С‘Р в„– Р С”Р С‘РЎР‚Р С‘Р В»Р В»Р С‘РЎвЂ Р В°
                nameLower.equals("dona") ||                          // РЎС“Р В·Р В±Р ВµР С”РЎРѓР С”Р С‘Р в„– Р В»Р В°РЎвЂљР С‘Р Р…Р С‘РЎвЂ Р В°
                nameLower.equals("pcs") || nameLower.equals("piece") || nameLower.equals("pieces")) {
                return "pcs";
            }
        }

        // Р СџР С• РЎС“Р СР С•Р В»РЎвЂЎР В°Р Р…Р С‘РЎР‹ РІР‚вЂќ РЎв‚¬РЎвЂљРЎС“Р С”Р С‘
        log.debug("[SBGPay] Unknown measure unit: rcumCode={}, measureName={}, defaulting to 'pcs'",
            rcumCode, measureName);
        return "pcs";
    }

    // ====================
    // HTTP CLIENT
    // ====================

    private JsonNode httpGet(String url) throws Exception {
        return httpGet(url, DEFAULT_HTTP_TIMEOUT_MS);
    }

    private JsonNode httpGet(String url, int timeoutMs) throws Exception {
        long startTime = System.currentTimeMillis();
        int effectiveTimeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_HTTP_TIMEOUT_MS;

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Device-Token", deviceToken);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(effectiveTimeoutMs);
        conn.setReadTimeout(effectiveTimeoutMs);

        try {
            int status = conn.getResponseCode();
            String body = readResponseBody(conn, status);

            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("[HTTP] GET {} -> {} ({}ms, timeout={}ms)", url, status, elapsed, effectiveTimeoutMs);

            if (status >= 400) {
                handleHttpError(status, body);
            }

            return objectMapper.readTree(body.isEmpty() ? "{}" : body);
        } finally {
            conn.disconnect();
        }
    }

    private JsonNode httpPost(String url, Map<String, Object> body, String idempotencyKey) throws Exception {
        return httpPost(url, body, idempotencyKey, true);
    }

    private JsonNode httpPost(String url,
                              Map<String, Object> body,
                              String idempotencyKey,
                              boolean withBody) throws Exception {
        long startTime = System.currentTimeMillis();

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Device-Token", deviceToken);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Idempotency-Key", idempotencyKey);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        if (withBody) {
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            Map<String, Object> effectiveBody = body != null ? body : Collections.emptyMap();
            byte[] payload = objectMapper.writeValueAsBytes(effectiveBody);

            log.debug("[HTTP] POST {} Request body: {}", url, new String(payload, StandardCharsets.UTF_8));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload);
            }
        } else {
            conn.setDoOutput(false);
            log.debug("[HTTP] POST {} Request body: <empty>", url);
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

            return objectMapper.readTree(responseBody.isEmpty() ? "{}" : responseBody);
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
        String title = null;

        try {
            JsonNode error = objectMapper.readTree(body);
            if (error.has("detail")) {
                detail = error.get("detail").asText();
            } else if (error.has("title")) {
                detail = error.get("title").asText();
            }
            if (error.has("title")) {
                title = error.get("title").asText();
            }
        } catch (IOException ignored) {}

        if (status == 401) {
            throw new Exception(getString("error.unauthorized", "Р С›РЎв‚¬Р С‘Р В±Р С”Р В° Р В°Р Р†РЎвЂљР С•РЎР‚Р С‘Р В·Р В°РЎвЂ Р С‘Р С‘: Р С—РЎР‚Р С•Р Р†Р ВµРЎР‚РЎРЉРЎвЂљР Вµ Device-Token"));
        } else if (status == 429) {
            throw new Exception(getString("error.rate.limit", "Р РЋР В»Р С‘РЎв‚¬Р С”Р С•Р С Р СР Р…Р С•Р С–Р С• Р В·Р В°Р С—РЎР‚Р С•РЎРѓР С•Р Р†, Р С—Р С•Р С—РЎР‚Р С•Р В±РЎС“Р в„–РЎвЂљР Вµ Р С—Р С•Р В·Р В¶Р Вµ"));
        } else {
            throw new HttpStatusException(status, title, detail);
        }
    }

    private boolean isAlreadyCompletedConflict(HttpStatusException e) {
        if (e == null || e.status != 409) {
            return false;
        }

        String detail = e.detail != null ? e.detail.toLowerCase() : "";
        String title = e.title != null ? e.title.toLowerCase() : "";

        return detail.contains("status 'completed'")
            || detail.contains("status \"completed\"")
            || detail.contains("already completed")
            || detail.contains("cannot complete payment")
            || title.contains("invalid_status");
    }

    private boolean isAlreadyReversedConflict(HttpStatusException e) {
        if (e == null || e.status != 409) {
            return false;
        }

        String detail = e.detail != null ? e.detail.toLowerCase() : "";
        String title = e.title != null ? e.title.toLowerCase() : "";

        return detail.contains("already reversed")
            || detail.contains("already refunded")
            || detail.contains("status 'reversed'")
            || detail.contains("status \"reversed\"")
            || detail.contains("cannot reverse payment")
            || detail.contains("cannot reversal payment")
            || title.contains("invalid_status");
    }

    private boolean isCancelConflictForCompleted(HttpStatusException e) {
        if (e == null || e.status != 409) {
            return false;
        }

        String detail = e.detail != null ? e.detail.toLowerCase() : "";
        return detail.contains("status 'completed'")
            || detail.contains("status \"completed\"")
            || detail.contains("already completed")
            || detail.contains("cannot cancel payment with status 'completed'")
            || detail.contains("cannot cancel payment with status \"completed\"");
    }

    private static class HttpStatusException extends Exception {
        private final int status;
        private final String title;
        private final String detail;

        private HttpStatusException(int status, String title, String detail) {
            super(detail != null ? detail : "HTTP " + status);
            this.status = status;
            this.title = title;
            this.detail = detail;
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

            String title = currentMethodName != null && !currentMethodName.isEmpty()
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
                    getString("scan.qr", "Р РЋР С”Р В°Р Р…Р С‘РЎР‚РЎС“Р в„–РЎвЂљР Вµ QR Р Т‘Р В»РЎРЏ Р С•Р С—Р В»Р В°РЎвЂљРЎвЂ№"),
                    qrCodeData,
                    amount
                );
                message.setAutoCloseable(false);

                Duration displayDuration = Duration.ofSeconds(Math.min(ttlSeconds, pollTimeoutSeconds));
                CustomerDisplayMessage displayMessage = new CustomerDisplayMessage(message, displayDuration);

                customerDisplay.display(displayMessage);
                log.info("[SBGPay] QR displayed on customer display");
            } else {
                String text = title + "\n" + getString("amount", "Р С™ Р С•Р С—Р В»Р В°РЎвЂљР Вµ: ") + amount + " " + currency;
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
        String text = getString("waiting.payment", "Р С›Р В¶Р С‘Р Т‘Р В°Р Р…Р С‘Р Вµ Р С•Р С—Р В»Р В°РЎвЂљРЎвЂ№...");
        if (currentPaymentCode != null && !currentPaymentCode.isEmpty()) {
            text += "\n" + getString("payment.code", "Р С™Р С•Р Т‘: ") + currentPaymentCode;
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
        loadConfiguration(false);
    }

    private void loadConfiguration(boolean verboseDebug) {
        try {
            PropertiesReader serviceProps = integrationProperties.getServiceProperties();
            PropertiesReader pluginProps = integrationProperties.getPluginProperties();

            if (verboseDebug && log.isTraceEnabled()) {
                log.trace("[SBGPay] Service property keys: {}", safePropertyKeys(serviceProps));
                log.trace("[SBGPay] Plugin property keys: {}", safePropertyKeys(pluginProps));
            }

            baseUrl = readStringOption(serviceProps, pluginProps, "sbgpay.baseUrl", "https://sbg.amasia.io/pos", verboseDebug);
            deviceToken = readStringOption(serviceProps, pluginProps, "sbgpay.deviceToken", null, verboseDebug);
            language = readStringOption(serviceProps, pluginProps, "sbgpay.lang", "ru", verboseDebug);
            currency = readStringOption(serviceProps, pluginProps, "sbgpay.currency", "UZS", verboseDebug);

            ttlSeconds = readIntOption(serviceProps, pluginProps, "sbgpay.ttlSeconds", 300, 60, 3600, verboseDebug);
            pollDelayMs = readIntOption(serviceProps, pluginProps, "sbgpay.pollDelayMs", 2000, 500, 10000, verboseDebug);
            pollTimeoutSeconds = readIntOption(serviceProps, pluginProps, "sbgpay.pollTimeoutSeconds", 420, 60, 7200, verboseDebug);

            sendReceipt = readBooleanOption(serviceProps, pluginProps, "sbgpay.sendReceipt", false, verboseDebug);

            logConfigurationIfChanged();
        } catch (Exception e) {
            log.error("[SBGPay] Failed to load configuration", e);
        }
    }

    private String readStringOption(PropertiesReader serviceProps,
                                    PropertiesReader pluginProps,
                                    String key,
                                    String defaultValue,
                                    boolean verboseDebug) {
        String serviceRaw = safeGetProperty(serviceProps, key);
        String pluginRaw = safeGetProperty(pluginProps, key);

        String resolved = hasText(serviceRaw)
            ? serviceRaw.trim()
            : hasText(pluginRaw) ? pluginRaw.trim() : defaultValue;

        String source = hasText(serviceRaw)
            ? "service"
            : hasText(pluginRaw) ? "plugin" : "default";
        if (verboseDebug && log.isTraceEnabled()) {
            log.trace("[SBGPay] String option '{}': serviceRaw={}, pluginRaw={}, resolved={}, source={}",
                key, rawForLog(serviceRaw), rawForLog(pluginRaw), rawForLog(resolved), source);
        }

        return resolved;
    }

    private boolean readBooleanOption(PropertiesReader serviceProps,
                                      PropertiesReader pluginProps,
                                      String key,
                                      boolean defaultValue,
                                      boolean verboseDebug) {
        String serviceRaw = safeGetProperty(serviceProps, key);
        String pluginRaw = safeGetProperty(pluginProps, key);

        Integer serviceValue = parseBooleanConfigValue(serviceRaw);
        Integer pluginValue = parseBooleanConfigValue(pluginRaw);

        if (serviceValue == null && hasText(serviceRaw)) {
            logWarnSafe("[SBGPay] Invalid boolean config in service '{}'='{}'", key, serviceRaw);
        }
        if (pluginValue == null && hasText(pluginRaw)) {
            logWarnSafe("[SBGPay] Invalid boolean config in plugin '{}'='{}'", key, pluginRaw);
        }

        boolean resolvedValue = defaultValue;
        String source = "default";
        if (serviceValue != null) {
            resolvedValue = serviceValue.intValue() == 1;
            source = "service";
        } else if (pluginValue != null) {
            resolvedValue = pluginValue.intValue() == 1;
            source = "plugin";
        }

        if (verboseDebug && log.isTraceEnabled()) {
            log.trace("[SBGPay] Boolean option '{}': serviceRaw={}, pluginRaw={}, resolved={}, source={}",
                key, rawForLog(serviceRaw), rawForLog(pluginRaw), resolvedValue, source);
        }

        return resolvedValue;
    }

    private int readIntOption(PropertiesReader serviceProps,
                              PropertiesReader pluginProps,
                              String key,
                              int defaultValue,
                              int minValue,
                              int maxValue,
                              boolean verboseDebug) {
        String serviceRawGet = safeGetProperty(serviceProps, key);
        String pluginRawGet = safeGetProperty(pluginProps, key);
        String serviceRawMap = safeGetPropertyFromAll(serviceProps, key);
        String pluginRawMap = safeGetPropertyFromAll(pluginProps, key);

        String serviceRaw = hasText(serviceRawGet) ? serviceRawGet : serviceRawMap;
        String pluginRaw = hasText(pluginRawGet) ? pluginRawGet : pluginRawMap;

        Integer serviceParsed = parseIntegerConfigValue(serviceRaw);
        Integer pluginParsed = parseIntegerConfigValue(pluginRaw);

        Integer serviceInt = serviceParsed != null
            ? serviceParsed
            : firstNonNull(
                safeGetInt(serviceProps, key),
                safeGetIntWithDefaultMarker(serviceProps, key, Integer.MIN_VALUE));
        Integer pluginInt = pluginParsed != null
            ? pluginParsed
            : firstNonNull(
                safeGetInt(pluginProps, key),
                safeGetIntWithDefaultMarker(pluginProps, key, Integer.MIN_VALUE));

        Integer resolved = serviceInt != null ? serviceInt : pluginInt;
        String source = serviceInt != null ? "service" : pluginInt != null ? "plugin" : "default";

        if (serviceParsed == null && serviceRaw != null && !serviceRaw.trim().isEmpty()) {
            logWarnSafe("[SBGPay] Invalid numeric config in service '{}'='{}'", key, serviceRaw);
        }
        if (pluginParsed == null && pluginRaw != null && !pluginRaw.trim().isEmpty()) {
            logWarnSafe("[SBGPay] Invalid numeric config in plugin '{}'='{}'", key, pluginRaw);
        }

        int resolvedValue = resolved != null ? resolved : defaultValue;

        if (resolvedValue < minValue || resolvedValue > maxValue) {
            logWarnSafe("[SBGPay] Out-of-range config '{}': {} (allowed {}..{}), using default {}",
                key, resolvedValue, minValue, maxValue, defaultValue);
            resolvedValue = defaultValue;
            source = "default(out-of-range)";
        }

        if (verboseDebug && log.isTraceEnabled()) {
            log.trace("[SBGPay] Numeric option '{}': serviceRawGet={}, serviceRawMap={}, pluginRawGet={}, pluginRawMap={}, serviceInt={}, pluginInt={}, resolved={}, source={}",
                key, rawForLog(serviceRawGet), rawForLog(serviceRawMap), rawForLog(pluginRawGet), rawForLog(pluginRawMap), serviceInt, pluginInt, resolvedValue, source);
        }

        return resolvedValue;
    }

    private boolean hasRequiredConfiguration() {
        if (!hasText(baseUrl)) {
            log.info("[SBGPay] isAvailable=false: baseUrl not configured");
            return false;
        }
        if (!hasText(deviceToken)) {
            log.info("[SBGPay] isAvailable=false: deviceToken not configured");
            return false;
        }
        return true;
    }

    private void logConfigurationIfChanged() {
        String snapshot = "baseUrl=" + rawForLog(baseUrl)
            + ", lang=" + rawForLog(language)
            + ", currency=" + rawForLog(currency)
            + ", ttl=" + ttlSeconds
            + ", pollDelay=" + pollDelayMs
            + ", pollTimeout=" + pollTimeoutSeconds
            + ", sendReceipt=" + sendReceipt;

        if (!snapshot.equals(lastLoggedConfigSnapshot)) {
            lastLoggedConfigSnapshot = snapshot;
            log.debug("[SBGPay] Config: {}", snapshot);
        }
    }

    private String safeGetProperty(PropertiesReader props, String key) {
        if (props == null) {
            return null;
        }
        try {
            return props.get(key);
        } catch (Exception e) {
            logWarnSafe("[SBGPay] Failed to read config key '{}': {}", key, e.getMessage());
            return null;
        }
    }

    private List<String> safePropertyKeys(PropertiesReader props) {
        if (props == null) {
            return Collections.emptyList();
        }
        try {
            Map<String, String> all = props.getAllProperties();
            if (all == null || all.isEmpty()) {
                return Collections.emptyList();
            }
            return new ArrayList<>(all.keySet());
        } catch (Exception e) {
            logWarnSafe("[SBGPay] Failed to read property keys: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String safeGetPropertyFromAll(PropertiesReader props, String key) {
        if (props == null) {
            return null;
        }
        try {
            Map<String, String> all = props.getAllProperties();
            if (all == null) {
                return null;
            }
            return all.get(key);
        } catch (Exception e) {
            logWarnSafe("[SBGPay] Failed to read key '{}' from all properties: {}", key, e.getMessage());
            return null;
        }
    }

    private Integer safeGetInt(PropertiesReader props, String key) {
        if (props == null) {
            return null;
        }
        try {
            return props.getInt(key);
        } catch (Exception e) {
            logWarnSafe("[SBGPay] Failed to read int config key '{}': {}", key, e.getMessage());
            return null;
        }
    }

    private Integer safeGetIntWithDefaultMarker(PropertiesReader props, String key, int marker) {
        if (props == null) {
            return null;
        }
        try {
            int value = props.getInt(key, marker);
            return value == marker ? null : value;
        } catch (Exception e) {
            logWarnSafe("[SBGPay] Failed to read int(default) config key '{}': {}", key, e.getMessage());
            return null;
        }
    }

    private Integer firstNonNull(Integer first, Integer second) {
        return first != null ? first : second;
    }

    private Integer parseIntegerConfigValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String normalized = rawValue.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        normalized = normalized.replace(" ", "").replace("_", "");

        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            // fallback below
        }

        try {
            BigDecimal decimal = new BigDecimal(normalized.replace(',', '.'));
            return decimal.intValueExact();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer parseBooleanConfigValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String normalized = rawValue.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return null;
        }

        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)
            || "y".equals(normalized) || "on".equals(normalized)) {
            return Integer.valueOf(1);
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)
            || "n".equals(normalized) || "off".equals(normalized)) {
            return Integer.valueOf(0);
        }

        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String rawForLog(String value) {
        return value == null ? "<null>" : "'" + value + "'";
    }

    private void logWarnSafe(String message, Object... args) {
        if (log != null) {
            log.warn(message, args);
        }
    }

    private void resetState() {
        stopStatusPolling();
        currentPaymentId = null;
        currentPaymentCode = null;
        currentMethodId = null;
        currentMethodName = null;
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

    private String resolveErrorMessage(Exception error, String prefixKey, String prefixDefault) {
        if (isRequestTimeoutException(error)) {
            return getString("error.processing.timeout",
                "Превышено время ожидания ответа от процессинга. Повторите операцию.");
        }

        if (isCommunicationException(error)) {
            return getString("error.communication",
                "Нет связи с процессингом. Проверьте сеть и повторите операцию.");
        }

        String details = extractErrorDetails(error);
        if (!hasText(details)) {
            return getString(prefixKey, prefixDefault);
        }

        return getString(prefixKey, prefixDefault) + details;
    }

    private String extractErrorDetails(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (hasText(current.getMessage())) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return getString("error.unknown", "Unknown error");
    }

    private boolean isRequestTimeoutException(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isCommunicationException(Throwable error) {
        if (isRequestTimeoutException(error)) {
            return false;
        }

        Throwable current = error;
        while (current != null) {
            if (current instanceof UnknownHostException
                || current instanceof ConnectException
                || current instanceof NoRouteToHostException
                || current instanceof SocketException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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
            return "Р Т‘Р С• " + fromMinorUnits(method.maxAmount);
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

    private String getQrData(PaymentStatus status) {
        if (status == null) {
            return null;
        }
        if (status.qrPayload != null && !status.qrPayload.isEmpty()) {
            return status.qrPayload;
        }
        if (status.qrCodeData != null && !status.qrCodeData.isEmpty()) {
            return status.qrCodeData;
        }
        return null;
    }

    private int calculateStatusRequestTimeoutMs(long remainingMs) {
        if (remainingMs <= 1L) {
            return 1;
        }
        return (int) Math.min(remainingMs, DEFAULT_HTTP_TIMEOUT_MS);
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

    private boolean isRefundSuccessStatus(String status) {
        return "refunded".equalsIgnoreCase(status);
    }

    private boolean isRefundFailedStatus(String status) {
        return "refund_failed".equalsIgnoreCase(status);
    }

    private boolean isCancelledStatus(String status) {
        return "cancelled".equalsIgnoreCase(status) || "canceled".equalsIgnoreCase(status);
    }

    private boolean isReversedStatus(String status) {
        return "reversed".equalsIgnoreCase(status);
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

    private static class RefundResponse {
        String refundId;
        String refundCode;
        String status;
        String errorMessage;
    }

}





