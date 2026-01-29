package com.example.therapify.repository;
import com.example.therapify.enums.UserType;
import com.example.therapify.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {
    Optional<User> findByEmail(String email);
    List<User> findByUserType(UserType userType);;
    List<User> findByFirstName(String nombre);
    List<User> findByLastName(String apellido);
}
