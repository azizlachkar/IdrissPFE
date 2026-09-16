package com.cmrt.pfe.repositories;

import com.cmrt.pfe.models.User;
import com.cmrt.pfe.models.enums.Role;
import com.cmrt.pfe.models.enums.ServiceUnit;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByVerificationToken(String token);
    Optional<User> findByResetToken(String token);
    List<User> findByRole(Role role);
    List<User> findByRoleIn(Collection<Role> roles);
    List<User> findByServiceUnit(ServiceUnit serviceUnit);
    List<User> findByActiveTrue();
    boolean existsByEmailIgnoreCase(String email);
}
