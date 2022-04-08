package at.lucny.p2pbackup.core.repository;

import at.lucny.p2pbackup.core.domain.CloudUpload;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CloudUploadRepository extends JpaRepository<CloudUpload, String> {

    long countByShareUrlIsNull();

    long countByShareUrlIsNotNull();

    Page<CloudUpload> findAllByShareUrlIsNull(Pageable pageRequest);

    Page<CloudUpload> findAllByShareUrlIsNotNull(Pageable pageRequest);

    Optional<CloudUpload> findByBlockMetaDataId(String id);

    @Query("SELECT cu.id FROM CloudUpload cu " +
            " WHERE cu.shareUrl IS NOT NULL ")
    Page<String> findIdByShareUrlIsNotNull(Pageable pageRequest);

}
