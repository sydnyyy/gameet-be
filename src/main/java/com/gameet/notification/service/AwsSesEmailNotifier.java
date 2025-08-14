package com.gameet.notification.service;

import com.gameet.common.util.Map2JsonSerializer;
import com.gameet.global.config.aws.AwsSesProperties;
import com.gameet.notification.dto.TemplatedEmailRequest;
import com.gameet.notification.enums.AwsSesTemplateType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AwsSesEmailNotifier {

    private final AwsSesProperties awsSesProperties;

    private final SesV2Client sesV2Client;
    private final Map2JsonSerializer jsonSerializer;

    public void sendTemplatedEmail(TemplatedEmailRequest templatedEmailRequest) {
        try {
            SendEmailRequest emailRequest = SendEmailRequest.builder()
                    .destination(getDestination(templatedEmailRequest.toEmail()))
                    .content(getEmailContent(templatedEmailRequest.awsSesTemplateType(), templatedEmailRequest.templateData()))
                    .fromEmailAddress(awsSesProperties.fromEmailAddress())
                    .build();

            sesV2Client.sendEmail(emailRequest);
        } catch (SesV2Exception e) {
            log.error("[sendTemplatedEmail] 이메일 전송 실패. toEmail={}, 오류={}", templatedEmailRequest.toEmail(), e.getMessage(), e);
        }
    }

    public void sendBulkTemplatedEmail(List<TemplatedEmailRequest> templatedEmailRequests) {
        if (templatedEmailRequests == null || templatedEmailRequests.isEmpty()) {
            return;
        }

        BulkEmailContent defaultBulkEmailContent = BulkEmailContent.builder()
                .template(getTemplate(templatedEmailRequests.getFirst().awsSesTemplateType(), Map.of("time", "{}")))
                .build();

        List<BulkEmailEntry> bulkEmailEntries = new ArrayList<>();
        for (TemplatedEmailRequest emailRequest : templatedEmailRequests) {
            ReplacementEmailContent replacementEmailContent = ReplacementEmailContent.builder()
                    .replacementTemplate(ReplacementTemplate.builder()
                            .replacementTemplateData(jsonSerializer.serializeAsString(emailRequest.templateData()))
                            .build())
                    .build();

            BulkEmailEntry bulkEmailEntry = BulkEmailEntry.builder()
                    .destination(getDestination(emailRequest.toEmail()))
                    .replacementEmailContent(replacementEmailContent)
                    .build();

            bulkEmailEntries.add(bulkEmailEntry);
        }

        try {
            SendBulkEmailRequest bulkEmailRequest = SendBulkEmailRequest.builder()
                    .defaultContent(defaultBulkEmailContent)
                    .bulkEmailEntries(bulkEmailEntries)
                    .fromEmailAddress(awsSesProperties.fromEmailAddress())
                    .build();

            sesV2Client.sendBulkEmail(bulkEmailRequest);
        } catch (SesV2Exception e) {
            log.error("[sendBulkTemplatedEmail] 이메일 전송 실패. 오류={}", e.getMessage(), e);
        }
    }

    private Destination getDestination(String toEmail) {
        return Destination.builder()
                .toAddresses(toEmail)
                .build();
    }

    private EmailContent getEmailContent(AwsSesTemplateType templateType, Map<String, String> templateData) {
        return EmailContent.builder()
                .template(getTemplate(templateType, templateData))
                .build();
    }

    private Template getTemplate(AwsSesTemplateType templateType, Map<String, String> templateData) {
        return Template.builder()
                    .templateName(templateType.getTemplateName())
                    .templateData(jsonSerializer.serializeAsString(templateData))
                    .build();
    }
}
