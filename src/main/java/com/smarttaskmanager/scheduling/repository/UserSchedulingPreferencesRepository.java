package com.smarttaskmanager.scheduling.repository;

import com.smarttaskmanager.scheduling.entity.UserSchedulingPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for UserSchedulingPreferences entities.
 */
@Repository
public interface UserSchedulingPreferencesRepository extends JpaRepository<UserSchedulingPreferences, Long> {

    Optional<UserSchedulingPreferences> findByUserId(Long userId);
}
