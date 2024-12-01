package at.lucny.p2pbackup.verification.repository;

import at.lucny.p2pbackup.verification.domain.VerificationValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerificationValueRepository extends JpaRepository<VerificationValue, String> {

    long countByBlockMetaDataId(String blockMetaDataId);

    List<VerificationValue> findByBlockMetaDataIdOrderByIdAsc(String blockMetaDataId);
}
