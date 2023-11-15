package at.lucny.p2pbackup.restore.repository;

import at.lucny.p2pbackup.restore.domain.RestoreBlockData;
import at.lucny.p2pbackup.restore.domain.RestorePath;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;

public interface RestorePathRepository extends JpaRepository<RestorePath, String> {

    @Query("SELECT count(p) FROM RestorePath p " +
            "WHERE p.missingBlocks IS EMPTY")
    long countWithoutMissingBlocks();

    @Query("SELECT p FROM RestorePath p " +
            "WHERE p.missingBlocks IS EMPTY")
    Page<RestorePath> findWithoutMissingBlocks(Pageable page);

    /**
     * Finds all {@link RestorePath}-entities that contain the given {@link RestoreBlockData}.
     * Sets a pessimistic-write-lock on the {@link RestorePath}-entities
     *
     * @param restoreBlockData the {@link RestoreBlockData} that must be contained in the returned {@link RestorePath}s
     * @return a list of {@link RestorePath}-entities
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM RestorePath p " +
            "WHERE :restoreBlockData MEMBER OF p.missingBlocks")
    List<RestorePath> findByRestoreBlockData(@Param("restoreBlockData") RestoreBlockData restoreBlockData);

    @Query("SELECT p FROM RestorePath p " +
            "LEFT JOIN FETCH p.missingBlocks")
    List<RestorePath> findAllFetchMissingBlocks();
}
