package com.kingdom.Repository;

import com.kingdom.Model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    User findUserById(Integer id);
    User findUserByUsername(String username);

    // Added by Anas (WhatsApp volunteer flow): match an inbound WhatsApp sender to their account by phone.
    User findUserByPhoneNumber(String phoneNumber);
}
