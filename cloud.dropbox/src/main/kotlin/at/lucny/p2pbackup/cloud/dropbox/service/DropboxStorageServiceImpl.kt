package at.lucny.p2pbackup.cloud.dropbox.service

import at.lucny.p2pbackup.application.config.P2PBackupProperties
import at.lucny.p2pbackup.cloud.CloudStorageService
import com.dropbox.core.DbxException
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.CreateFolderErrorException
import com.dropbox.core.v2.files.GetMetadataErrorException
import com.dropbox.core.v2.files.WriteMode
import org.apache.commons.configuration2.Configuration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.inputStream


private const val FOLDER_STORAGE: String = "at.lucny.p2pbackup.cloud.dropbox"

private const val PROVIDER_ID = "at.lucny.p2pbackup.cloud.dropbox.service.DropboxStorageServiceImpl"

@Service
@Validated
class DropboxStorageServiceImpl(
    val p2PBackupProperties: P2PBackupProperties,
    val configuration: Configuration
) : CloudStorageService {

    private val LOGGER: Logger = LoggerFactory.getLogger(DropboxStorageServiceImpl::class.java)

    private val CONFIG_KEY_ENABLED = "at.lucny.p2pbackup.cloud.dropbox.service.ENABLED"

    private val ACCESS_TOKEN =
        "sl.BrBZykPEbwhNXbOyWUYu9jwUexjou81GwwLVMGDoupUGsi9Fxap1ZzH3E1gOXsm-6k5PISrhhYtW_Q7L7efj7CK63G3QBn5IO1uzGIe4mQe9quaeM1RHKTQgB42Z4jUcn0mLy5l0HuIK"

    lateinit var dropboxClient: DbxClientV2

    override fun getId(): String {
        return PROVIDER_ID;
    }

    override fun configure(config: MutableMap<String, String>) {
        if (config.containsKey("enabled") && config["enabled"].toBoolean()) {
            this.configuration.setProperty(CONFIG_KEY_ENABLED, true)
            this.initialize()
        }
    }

    private fun initialize() {
        if (!this.configuration.getBoolean(CONFIG_KEY_ENABLED, false)) {
            LOGGER.info("dropbox not enabled")
            return
        }

        // Create Dropbox client
        val config = DbxRequestConfig.newBuilder("p2pbackup.dropbox.service").build()
        this.dropboxClient = DbxClientV2(config, ACCESS_TOKEN)

        try {
            val metadata = this.dropboxClient.files().getMetadata(FOLDER_STORAGE)
        } catch (e: GetMetadataErrorException) {
            if (e.errorValue.isPath) {
                val le = e.errorValue.pathValue
                if (le.isNotFound() || le.isNotFolder) {
                    try {
                        LOGGER.info("creating folder '$FOLDER_STORAGE' on dropbox")
                        // create folder
                        val createFolderResult = this.dropboxClient.files().createFolderV2(FOLDER_STORAGE)
                        LOGGER.debug(
                            "folder '$FOLDER_STORAGE' has id ${createFolderResult.metadata.id}"
                        )
                    } catch (e1: CreateFolderErrorException) {
                        throw IllegalStateException("unable to initialize dropbox", e)
                    } catch (e1: DbxException) {
                        throw IllegalStateException("unable to initialize dropbox", e)
                    }
                }
            }
        }

        LOGGER.info("initialized dropbox")
    }

    override fun isInitialized(): Boolean {
        return ::dropboxClient.isInitialized
    }

    private fun checkInitialized() {
        check(this.isInitialized) { "dropbox wasn't initialized" }
    }

    private fun getRemotePath(filename: String): String {
        return "$FOLDER_STORAGE/$filename"
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

    override fun share(filename: String): String {
        LOGGER.trace("begin share(filename={})", filename)
        this.checkInitialized()

        val remoteFilename = this.getRemotePath(filename)
        val metaData = this.dropboxClient.sharing().createSharedLinkWithSettings(remoteFilename)

        LOGGER.trace("end share: return={}", metaData.url)
        return metaData.url
    }

    override fun delete(filename: String) {
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