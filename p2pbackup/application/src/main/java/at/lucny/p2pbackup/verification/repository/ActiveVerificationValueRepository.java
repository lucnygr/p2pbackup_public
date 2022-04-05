package at.lucny.p2pbackup.verification.repository;

import at.lucny.p2pbackup.verification.domain.ActiveVerificationValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ActiveVerificationValueRepository extends JpaRepository<ActiveVerificationValue, String> {

    Optional<ActiveVerificationValue> findByBlockMetaDataId(String blockMetaDataId);
}
