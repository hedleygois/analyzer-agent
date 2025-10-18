package email

import config.EmailConfig
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.Properties

class EmailService(private val cfg: EmailConfig) {
    private val session: Session by lazy {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", cfg.smtpHost)
            put("mail.smtp.port", cfg.smtpPort.toString())
        }
        Session.getInstance(props, null)
    }

    fun send(subject: String, html: String) {
        val msg = MimeMessage(session)
        msg.setFrom(InternetAddress(cfg.fromEmail))
        cfg.toEmails.forEach { recipient ->
            msg.addRecipient(Message.RecipientType.TO, InternetAddress(recipient))
        }
        msg.subject = subject
        msg.setContent(html, "text/html; charset=utf-8")
        Transport.send(msg, cfg.username, cfg.password)
    }
}


