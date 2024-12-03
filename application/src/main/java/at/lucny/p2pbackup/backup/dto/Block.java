package at.lucny.p2pbackup.backup.dto;

import java.nio.ByteBuffer;

public record Block(ByteBuffer content, String hash) {
}
