package com.cmrt.pfe.repositories;

import com.cmrt.pfe.models.TechnicalDocument;
import com.cmrt.pfe.models.enums.DocumentStatus;
import com.cmrt.pfe.models.enums.DocumentType;
import com.cmrt.pfe.models.enums.StageType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends MongoRepository<TechnicalDocument, String> {
    List<TechnicalDocument> findByProductId(String productId);
    List<TechnicalDocument> findByProductIdAndStageType(String productId, StageType stageType);
    List<TechnicalDocument> findByProductIdAndType(String productId, DocumentType type);
    List<TechnicalDocument> findByStatus(DocumentStatus status);
    long countByStatus(DocumentStatus status);
    void deleteByProductId(String productId);
}
