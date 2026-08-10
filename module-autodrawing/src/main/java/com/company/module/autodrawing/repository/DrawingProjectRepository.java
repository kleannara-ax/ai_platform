package com.company.module.autodrawing.repository;

import com.company.module.autodrawing.entity.DrawingProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DrawingProjectRepository extends JpaRepository<DrawingProject, Long> {

    List<DrawingProject> findByTeamIdOrderByUpdatedAtDesc(String teamId);

    Optional<DrawingProject> findByProjectUuid(String projectUuid);

    void deleteByProjectUuid(String projectUuid);
}
