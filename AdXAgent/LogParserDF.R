#Use the below line of code to run the logparser into a txt file (adjusting filepath and output name accordingly)
# java -cp "lib/*" se.sics.tasim.logtool.Main -handler tau.tac.adx.parser.GeneralHandler -file ./../ExecutionLogs/game117.slg.gz  -bank > test.txt

# This function reads the input filename and returns a clean dataframe
# This currently only works for data frames
parseBalance <- function(inputFile) {
    logLines <- readLines(inputFile)
    logLines <- grep("^[0-9]", logLines, value = T)
    logTable <- read.table(text = logLines, sep = "\t", fill = T, stringsAsFactors = T, header = F)
    names(logTable) <- c("Day", "Agent", "Balance")
    logTable$Balance <- as.character(logTable$Balance)
    logTable$Balance <- gsub("Bank balance:       ", "", logTable$Balance)
    logTable$Balance <- as.numeric(logTable$Balance)
    return(logTable)
}

parseUCS <- function(inputFile) {
    logLines <- readLines(inputFile)
    logLines <- grep("^[0-9]", logLines, value = T)
    logTable <- read.table(text = logLines, sep = "\t", fill = T, stringsAsFactors = T, header = F)
    names(logTable) <- c("Day", "Agent", "Ucs")
    logTable$Ucs <- as.character(logTable$Ucs)
    logTable$Ucs <- gsub("UCS level:          ", "", logTable$Ucs)
    logTable$Ucs <- as.numeric(logTable$Ucs)
    return(logTable)
}

parseQ <- function(inputFile) {
    logLines <- readLines(inputFile)
    logLines <- grep("^[0-9]", logLines, value = T)
    logTable <- read.table(text = logLines, sep = "\t", fill = T, stringsAsFactors = T, header = F)
    names(logTable) <- c("Day", "Agent", "Q")
    logTable$Q <- as.character(logTable$Q)
    logTable$Q <- gsub("Quality rating:     ", "", logTable$Q)
    logTable$Q <- as.numeric(logTable$Q)
    return(logTable)
}

parseCamp <- function(inputFile) {
    logLines <- readLines(inputFile)
    logLines <- grep("^[0-9]", logLines, value = T)
    logTable <- read.table(text = logLines, sep = "\t", 
                           fill = T, stringsAsFactors = T, header = F, 
                           col.names = c("Day", "Agent", "Campaign", 
                                         "TargetedImps", "UntargetedImps", "Cost"), 
                           comment.char = "")
    logTable$Campaign <- as.character(logTable$Campaign)
    logTable$Campaign <- as.numeric(gsub("Campaign report:    #", "", logTable$Campaign))
    logTable$TargetedImps <- as.numeric(gsub(" Targeted Impressions: ", "", logTable$TargetedImps))
    logTable$UntargetedImps <- as.numeric(gsub("Non Targeted Impressions: ", "", logTable$UntargetedImps))
    logTable$Cost <- as.numeric(gsub(" Cost: ", "", logTable$Cost))
    return(logTable)
}



#ggplot(data = test2, aes(y = Balance, x = Day, colour = Agent)) + geom_line()
