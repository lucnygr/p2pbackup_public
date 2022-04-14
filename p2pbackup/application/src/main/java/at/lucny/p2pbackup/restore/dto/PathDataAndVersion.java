package at.lucny.p2pbackup.restore.dto;

import at.lucny.p2pbackup.core.domain.PathData;
import at.lucny.p2pbackup.core.domain.PathVersion;

public record PathDataAndVersion(PathData pathData, PathVersion pathVersion) {
}
