package com.cmrt.pfe.repositories;

import com.cmrt.pfe.models.Product;
import com.cmrt.pfe.models.enums.Customer;
import com.cmrt.pfe.models.enums.ProductStatus;
import com.cmrt.pfe.models.enums.ProjectType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    List<Product> findByProjectType(ProjectType projectType);

    List<Product> findByStatus(ProductStatus status);

    List<Product> findByCustomer(Customer customer);

    List<Product> findByBlockedTrue();

    Optional<Product> findByReferenceIgnoreCase(String reference);

    boolean existsByReferenceIgnoreCase(String reference);

    long countByProjectType(ProjectType projectType);

    long countByStatus(ProductStatus status);

    /** Every product where the given user is methodiste, qualiticien or project lead. */
    @Query("{ $or: [ { methodisteId: ?0 }, { qualiticienId: ?0 }, { chefProjetId: ?0 } ] }")
    List<Product> findByAnyOwner(String userId);

    /** Case-insensitive free-text match on reference or name. */
    @Query("{ $or: [ { reference: { $regex: ?0, $options: 'i' } }, { nom: { $regex: ?0, $options: 'i' } } ] }")
    List<Product> search(String term);
}
