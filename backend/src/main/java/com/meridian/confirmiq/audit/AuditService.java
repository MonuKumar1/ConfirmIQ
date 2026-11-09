package com.meridian.confirmiq.audit;

import com.meridian.confirmiq.security.CurrentUser;
import com.meridian.confirmiq.user.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository repository;

    /** Records an event as the current user (or the system outside a request). */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent record(Long tradeId, String entityType, Long entityId, String action,
                             String fromStatus, String toStatus, String details, String comment) {
        return record(CurrentUser.getOrSystem(), Instant.now(), tradeId, entityType, entityId, action,
                fromStatus, toStatus, details, comment);
    }

    /** Records an event with an explicit actor and timestamp (used by the demo data seeder). */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent record(CurrentUser actor, Instant at, Long tradeId, String entityType, Long entityId,
                             String action, String fromStatus, String toStatus, String details, String comment) {
        AuditEvent e = new AuditEvent();
        e.setTradeId(tradeId);
        e.setEntityType(entityType);
        e.setEntityId(entityId);
        e.setAction(action);
        e.setFromStatus(fromStatus);
        e.setToStatus(toStatus);
        e.setActor(actor.username());
        e.setActorRole(actor.roleName());
        e.setDetails(details);
        e.setComment(comment);
        e.setOccurredAt(at);
        return repository.save(e);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void staticData(String entityType, Long entityId, String action, String details) {
        record(null, entityType, entityId, action, null, null, details, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordLogin(AppUser user) {
        AuditEvent e = new AuditEvent();
        e.setEntityType("USER");
        e.setEntityId(user.getId());
        e.setAction("LOGIN");
        e.setActor(user.getUsername());
        e.setActorRole(user.getRole().name());
        repository.save(e);
    }
}
