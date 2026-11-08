package ru.trett.rss.server.services

import cats.effect.IO
import jakarta.mail.{Message, Session}
import jakarta.activation.DataHandler
import jakarta.mail.util.ByteArrayDataSource
import java.nio.charset.StandardCharsets
import jakarta.mail.internet.{InternetAddress, MimeMessage}
import org.eclipse.angus.mail.smtp.SMTPTransport
import org.jsoup.nodes.Entities
import ru.trett.rss.models.ChannelNews
import ru.trett.rss.server.config.DigestConfig
import java.util.Properties
import java.net.URI

class DigestMailer(config: DigestConfig):
    def send(recipient: String, subject: String, html: String): IO[Unit] = IO.blocking {
        require(
            config.smtpUsername.nonEmpty && config.smtpPassword.nonEmpty,
            "SMTP credentials are missing"
        )
        val properties = new Properties()
        properties.setProperty("mail.smtp.starttls.enable", "true")
        properties.setProperty("mail.smtp.starttls.required", "true")
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true")
        properties.setProperty("mail.smtp.connectiontimeout", "10000")
        properties.setProperty("mail.smtp.timeout", "30000")
        properties.setProperty("mail.smtp.writetimeout", "30000")
        val session = Session.getInstance(properties)
        val message = new MimeMessage(session)
        message.setFrom(new InternetAddress(config.smtpUsername, true))
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipient, true))
        message.setSubject(subject, "UTF-8")
        message.setDataHandler(
            new DataHandler(
                new ByteArrayDataSource(
                    html.getBytes(StandardCharsets.UTF_8),
                    "text/html; charset=UTF-8"
                )
            )
        )
        message.saveChanges()
        val transport = new SMTPTransport(
            session,
            new jakarta.mail.URLName(
                "smtp",
                "smtp.gmail.com",
                587,
                "",
                config.smtpUsername,
                config.smtpPassword
            )
        )
        try
            transport.connect("smtp.gmail.com", 587, config.smtpUsername, config.smtpPassword)
            transport.sendMessage(message, message.getAllRecipients)
        finally transport.close()
    }

object DigestMailer:
    def render(channels: List[ChannelNews]): String =
        val sections = channels.map { channel =>
            val items = channel.items.map { item =>
                val title = Entities.escape(item.title)
                val safeLink = scala.util
                    .Try(URI.create(item.link))
                    .toOption
                    .filter(uri =>
                        Set("https", "http").contains(uri.getScheme) && Option(uri.getHost).exists(
                            _.nonEmpty
                        )
                    )
                val heading = safeLink.fold(title)(uri =>
                    s"<a href=\"${Entities.escape(uri.toString)}\">$title</a>"
                )
                s"<article><h3>$heading</h3><p style=\"white-space:pre-wrap\">${Entities.escape(item.description)}</p></article>"
            }.mkString
            s"<section><h2>${Entities.escape(channel.channelTitle)}</h2>$items</section>"
        }.mkString
        s"<!doctype html><html lang=\"ru\"><head><meta charset=\"UTF-8\"></head><body><h1>Новости за последние 24 часа</h1>$sections</body></html>"
