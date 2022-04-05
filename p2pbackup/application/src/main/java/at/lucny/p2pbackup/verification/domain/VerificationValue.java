package at.lucny.p2pbackup.verification.domain;

import at.lucny.p2pbackup.core.domain.BlockMetaData;
import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "VERIFICATION_VALUE")
@Getter
@Setter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationValue extends AbstractVerificationValue {

    public VerificationValue(String id, BlockMetaData blockMetaData) {
        super(id, blockMetaData, null);
    }

    public VerificationValue(String id, BlockMetaData blockMetaData, String hash) {
        super(id, blockMetaData, hash);
    }


}
