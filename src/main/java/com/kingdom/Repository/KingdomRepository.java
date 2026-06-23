package com.kingdom.Repository;

import com.kingdom.Enums.KingdomType;
import com.kingdom.Model.Kingdom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KingdomRepository extends JpaRepository<Kingdom, Integer> {

    Kingdom findKingdomById(Integer id);
}
