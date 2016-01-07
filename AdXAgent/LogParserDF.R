#Use the below line of code to run the logparser into a txt file (adjusting filepath and output name accordingly)
# java -cp "lib/*" se.sics.tasim.logtool.Main -handler tau.tac.adx.parser.GeneralHandler -file ./../ExecutionLogs/game117.slg.gz  -bank > test.txt

# This function reads the input filename and returns a clean dataframe
# This currently only works for data frames
parseToDF <- function(inputFile) {
    logLines <- readLines(inputFile)
    logLines <- grep("^[0-9]", logLines, value = T)
    logTable <-read.table(text = logLines, sep = "\t", fill = T, stringsAsFactors = T, header = F)
    names(logTable) <- c("Day", "Agent", "Balance")
    logTable$Balance <- as.character(logTable$Balance)
    logTable$Balance <- gsub("Bank balance:       ", "", logTable$Balance)
    logTable$Balance <- as.numeric(logTable$Balance)
    return(logTable)
}

library(ggplot2)
ggplot(data = logTable, aes(y = Balance, x = Day, colour = Agent)) + geom_line()
