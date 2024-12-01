package at.lucny.p2pbackup.cloud.dropbox.service

import at.lucny.p2pbackup.application.config.P2PBackupProperties
import at.lucny.p2pbackup.cloud.CloudStorageService
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.inputStream

private const val FOLDER_STORAGE: String = "at.lucny.p2pbackup.cloud.nextcloud"

private const val PROVIDER_ID = "at.lucny.p2pbackup.cloud.dropbox.service.DropboxStorageServiceImpl"

@Service
@Validated
class DropboxStorageServiceImpl(val p2PBackupProperties: P2PBackupProperties) : CloudStorageService {

    private val LOGGER: Logger = LoggerFactory.getLogger(DropboxStorageServiceImpl::class.java)

    private val ACCESS_TOKEN =
        "sl.BrBZykPEbwhNXbOyWUYu9jwUexjou81GwwLVMGDoupUGsi9Fxap1ZzH3E1gOXsm-6k5PISrhhYtW_Q7L7efj7CK63G3QBn5IO1uzGIe4mQe9quaeM1RHKTQgB42Z4jUcn0mLy5l0HuIK"

    lateinit var dropboxClient: DbxClientV2

    override fun getId(): String {
        return PROVIDER_ID;
    }

    override fun configure(config: MutableMap<String, String>?) {
        // Create Dropbox client
        val config = DbxRequestConfig.newBuilder("p2pbackup.dropbox.service").build()
        this.dropboxClient = DbxClientV2(config, ACCESS_TOKEN)
    }

    override fun isInitialized(): Boolean {
        return ::dropboxClient.isInitialized
    }

    private fun checkInitialized() {
        check(this.isInitialized) { "dropbox wasn't initialized" }
    }

    private fun getRemotePath(filename: String): String {
        return FOLDER_STORAGE + "/" + filename
    }

    override fun upload(path: Path) {
        LOGGER.trace("begin upload(path={})", path)
        this.checkInitialized()
        require(Files.exists(path)) { "path $path does not exist" }
        require(Files.isReadable(path)) { "path $path is not readable" }
        require(Files.isRegularFile(path)) { "path $path is not a regular file" }

        val remoteFilename = this.getRemotePath(path.fileName.toString())
        path.inputStream().use { stream ->
            val result =
                this.dropboxClient.files().uploadBuilder(remoteFilename).withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(stream)
            LOGGER.debug("file {} has file-id {}", path, result.id)
        }

        LOGGER.trace("end upload")
    }

    override fun share(filename: String?): String {
        LOGGER.trace("begin share(filename={})", filename)
        this.checkInitialized()

        val remoteFilename = this.getRemotePath(filename)
        val metaData = this.dropboxClient.sharing().createSharedLinkWithSettings(remoteFilename)

        LOGGER.trace("end share: return={}", metaData.url)
        return metaData.url
    }

    override fun delete(filename: String?) {
        LOGGER.trace("begin delete(filename={})", filename)
        this.checkInitialized()

        val remoteFilename = this.getRemotePath(filename)
        this.dropboxClient.files().deleteV2(remoteFilename)

        LOGGER.trace("end delete(filename={})", filename)
    }

    override fun list(): MutableList<String> {
        this.checkInitialized()
        var result = this.dropboxClient.files().listFolderBuilder(FOLDER_STORAGE).withLimit(1000).start()
        val files: ArrayList<String> = arrayListOf()
        while (true) {
            result.entries.stream().forEach { e -> files.add(e.name) }
            if (!result.hasMore) {
                break;
            }
            result = this.dropboxClient.files().listFolderContinue(FOLDER_STORAGE)
        }

        return files
    }


    override fun toString(): String {
        return DropboxStorageServiceImpl::class.java.toString() + "-" + this.id
    }
}