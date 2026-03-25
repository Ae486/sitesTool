package com.rpacloud.notification;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private NotificationProperties properties;
    @InjectMocks private EmailService emailService;

    @Test
    void send_delegatesToMailSender() {
        org.mockito.Mockito.when(properties.getEmailFrom()).thenReturn("noreply@test.com");

        emailService.send("user@example.com", "Test Subject", "Test Body");

        org.mockito.ArgumentCaptor<SimpleMailMessage> captor =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage msg = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(msg.getTo()).containsExactly("user@example.com");
        org.assertj.core.api.Assertions.assertThat(msg.getSubject()).isEqualTo("Test Subject");
        org.assertj.core.api.Assertions.assertThat(msg.getText()).isEqualTo("Test Body");
        org.assertj.core.api.Assertions.assertThat(msg.getFrom()).isEqualTo("noreply@test.com");
    }
}
