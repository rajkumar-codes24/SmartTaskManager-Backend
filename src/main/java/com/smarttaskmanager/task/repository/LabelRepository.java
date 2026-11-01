package com.smarttaskmanager.task.repository;

import com.smarttaskmanager.task.entity.Label;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Label entities.
 */
@Repository
public interface LabelRepository extends JpaRepository<Label, Long> {

    @Query("SELECT l FROM Label l WHERE l.user.id = :userId ORDER BY l.name ASC")
    List<Label> findByUserId(@Param("userId") Long userId);

    @Query("SELECT l FROM Label l WHERE l.id = :labelId AND l.user.id = :userId")
    Optional<Label> findByIdAndUserId(@Param("labelId") Long labelId, @Param("userId") Long userId);

    @Query("SELECT CASE WHEN COUNT(l) > 0 THEN TRUE ELSE FALSE END FROM Label l WHERE l.user.id = :userId AND LOWER(l.name) = LOWER(:name)")
    boolean existsByUserIdAndNameIgnoreCase(@Param("userId") Long userId, @Param("name") String name);

    @Query("SELECT CASE WHEN COUNT(l) > 0 THEN TRUE ELSE FALSE END FROM Label l WHERE l.user.id = :userId AND LOWER(l.name) = LOWER(:name) AND l.id != :id")
    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(@Param("userId") Long userId, @Param("name") String name, @Param("id") Long id);
}
