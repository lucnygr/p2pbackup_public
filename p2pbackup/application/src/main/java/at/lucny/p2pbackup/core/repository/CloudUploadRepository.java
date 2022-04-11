package at.lucny.p2pbackup.core.repository;

import at.lucny.p2pbackup.core.domain.CloudUpload;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import javax.persistence.LockModeType;
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
    // use pessimistic-write-lock because its highly likely that multiple threads are trying to access and delete the same cloud-upload-entity
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CloudUpload> findByBlockMetaDataId(String id);

    /**
     * Loads the {@link CloudUpload}-entity and sets a write-lock
     *
     * @param id the id of the entity
     * @return an optional containing the entity
     */
    // use pessimistic-write-lock because its highly likely that multiple threads are trying to access and delete the same cloud-upload-entity
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CloudUpload> findById(String id);

    @Query("SELECT cu.id FROM CloudUpload cu " +
            " WHERE cu.shareUrl IS NOT NULL ")
    Page<String> findIdByShareUrlIsNotNull(Pageable pageRequest);

}
