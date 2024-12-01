package at.lucny.p2pbackup.restore.repository;

import at.lucny.p2pbackup.restore.domain.RecoverBackupIndex;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface RecoverBackupIndexRepository extends JpaRepository<RecoverBackupIndex, String> {

    boolean existsByDate(LocalDateTime date);

    List<RecoverBackupIndex> findByIdNot(String id);
}
