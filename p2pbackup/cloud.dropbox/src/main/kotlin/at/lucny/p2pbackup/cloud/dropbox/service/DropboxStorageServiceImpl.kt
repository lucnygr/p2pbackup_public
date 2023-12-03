package at.lucny.p2pbackup.cloud.dropbox.service

import at.lucny.p2pbackup.cloud.CloudStorageService
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated
import java.nio.file.Path


@Service
@Validated
class DropboxStorageServiceImpl : CloudStorageService {

    private val LOGGER: Logger = LoggerFactory.getLogger(DropboxStorageServiceImpl::class.java)

    val PROVIDER_ID = "at.lucny.p2pbackup.cloud.dropbox.service.DropboxStorageServiceImpl"

    private val ACCESS_TOKEN = "sl.BrBZykPEbwhNXbOyWUYu9jwUexjou81GwwLVMGDoupUGsi9Fxap1ZzH3E1gOXsm-6k5PISrhhYtW_Q7L7efj7CK63G3QBn5IO1uzGIe4mQe9quaeM1RHKTQgB42Z4jUcn0mLy5l0HuIK"

    val dropboxClient: DbxClientV2
    override fun getId(): String {
        return PROVIDER_ID;
    }

    override fun configure(config: MutableMap<String, String>?) {
        // Create Dropbox client
        val config = DbxRequestConfig.newBuilder("p2pbackup.dropbox.service").build()
        val client = DbxClientV2(config, ACCESS_TOKEN)
    }

    override fun isInitialized(): Boolean {
        TODO("Not yet implemented")
    }

    override fun upload(path: Path?) {
        TODO("Not yet implemented")
    }

    override fun share(filename: String?): String {
        TODO("Not yet implemented")
    }

    override fun delete(filename: String?) {
        TODO("Not yet implemented")
    }

    override fun list(): MutableList<String> {
        TODO("Not yet implemented")
    }
}