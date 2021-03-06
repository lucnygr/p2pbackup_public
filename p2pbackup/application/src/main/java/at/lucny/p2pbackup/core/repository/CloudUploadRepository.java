package at.lucny.p2pbackup.core.repository;

import at.lucny.p2pbackup.core.domain.CloudUpload;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CloudUploadRepository extends JpaRepository<CloudUpload, String> {

    long countByShareUrlIsNull();

    long countByShareUrlIsNotNull();

    Page<CloudUpload> findAllByShareUrlIsNull(Pageable pageRequest);

    Page<CloudUpload> findAllByShareUrlIsNotNull(Pageable pageRequest);

    /**
     * Loads the {@link CloudUpload}-entity and sets a write-lock
     *
     * @param id the block-meta-data-id of the block thats associated with the entity
     * @return an optional containing the entity
     */
    Optional<CloudUpload> findByBlockMetaDataId(String id);

    /**
     * Loads the {@link CloudUpload}-entity and sets a write-lock
     *
     * @param id the id of the entity
     * @return an optional containing the entity
     */
    Optional<CloudUpload> findById(String id);

    @Query("SELECT cu.id FROM CloudUpload cu " +
            " INNER JOIN cu.blockMetaData bmd " +
            " WHERE cu.shareUrl IS NOT NULL " +
            " AND ( " +
            "  SELECT COUNT(dl) FROM DataLocation dl " +
            "  WHERE dl.blockMetaData = bmd " +
            "  AND dl.userId IN (:userIds)" +
            " ) < :onlineUserCount ")
    Page<String> findIdByShareUrlIsNotNull(@Param("userIds") List<String> userIds, @Param("onlineUserCount") long onlineUserCount, Pageable pageRequest);

}
