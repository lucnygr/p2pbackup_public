Function Format-Bytes {
    Param
    (
        [Parameter(
            ValueFromPipeline = $true
        )]
        [ValidateNotNullOrEmpty()]
        [float]$number
    )
    Begin{
        $sizes = 'KB','MB','GB','TB','PB'
    }
    Process {
		if($number -eq 1) {
			return ""
		} elseif ($number -lt 1KB) {
		echo $number
            return "$number B"
        } elseif ($number -lt 1MB) {
            $number = $number / 1KB
            $number = [math]::Round($number,2)
            return "$number KB"
        } elseif ($number -lt 1GB) {
            $number = $number / 1MB
            $number = [math]::Round($number,2)
            return "$number MB"
        } else {
            $number = $number / 1GB
            $number = [math]::Round($number,2)
            return "$number GB"
        }
    }
    End{}
}

Get-ChildItem -Recurse data | select @{Name="Size";Expression={Format-Bytes $_.Length}},@{name="&";Expression={"&"}},@{name="Name";Expression={$_.FullName.replace("C:\Entwicklung\Testsetup\user1\data","").replace("\","/").replace("&","\&").replace("_","\_")}},@{name="EOL";Expression={"EOL"}} | Format-Table -Wrap -AutoSize  | Out-File filelist_user1.txt
