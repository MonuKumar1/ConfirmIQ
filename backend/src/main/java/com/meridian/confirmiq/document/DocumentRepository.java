package com.meridian.confirmiq.document;

import com.meridian.confirmiq.document.Document.Direction;
import com.meridian.confirmiq.document.Document.ProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {

    List<Document> findByTradeIdOrderByCreatedAtDescIdDesc(Long tradeId);

    @Query("select coalesce(max(d.version), 0) from Document d where d.trade.id = :tradeId and d.direction = :direction")
    int maxVersion(Long tradeId, Direction direction);

    Optional<Document> findFirstByTradeIdAndDirectionAndStageOrderByIdDesc(Long tradeId, Direction direction,
                                                                         Document.Stage stage);

    List<Document> findByDirectionAndProcessingStatusInOrderByCreatedAtDesc(Direction direction,
                                                                             Collection<ProcessingStatus> statuses);
}
