package com.meridian.confirmiq.outbox;

import com.meridian.confirmiq.outbox.OutboxMessage.MessageType;
import com.meridian.confirmiq.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxMessage queue(Long tradeId, MessageType type, String recipients, String cc,
                               String subject, String body, Long documentId) {
        return queue(tradeId, type, recipients, cc, subject, body, documentId,
                CurrentUser.getOrSystem().username(), Instant.now());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxMessage queue(Long tradeId, MessageType type, String recipients, String cc, String subject,
                               String body, Long documentId, String createdBy, Instant at) {
        OutboxMessage m = new OutboxMessage();
        m.setTradeId(tradeId);
        m.setMessageType(type);
        m.setRecipients(recipients);
        m.setCc(cc);
        m.setSubject(subject);
        m.setBody(body);
        m.setDocumentId(documentId);
        m.setCreatedBy(createdBy);
        m.setCreatedAt(at);
        return repository.save(m);
    }
}
