source('./../LogParserDF.R')
library(ggplot2)
library(dplyr)
gameList <- c(86:89, 95:96, 99:104, 110:117)

games <- list()
plots <- list()
for (i in 1:length(gameList)) {
    gameNumber <- gameList[i]
    logName <- paste0("bankLog", gameNumber, ".txt")
    games[[i]] <- parseBalance(logName)
    games[[i]]$Game <- gameNumber
    nam <- names(games)
    nam[i] <- paste0("game", gameNumber)
    names(games) <- nam
    plots[[i]] <- ggplot(data = games[[i]], aes(y = Balance, x = Day, colour = Agent)) + geom_line()
}

# Combine into one df for ggplot magic
allBankData <- data.frame()
for (i in 1:length(games)) {
    allBankData <- rbind(allBankData, games[[i]])
}
allBankDataNAMM <- allBankData %>% filter(as.numeric(Agent) == 5)


# GGplot magic
png("appdendix1.png", width = 1000, height = 600)
ggplot(data = allBankData, aes(y = Balance, x = Day, colour = Agent)) + 
    geom_line(alpha = 0.8) +
    geom_line(aes(x = Day, y = Balance, colour = "black"), data = allBankDataNAMM, colour = "black", size = 1.2) +
    facet_wrap( ~ Game, ncol=4, scales = "free_y") +
    theme_bw()
dev.off()



############### Repeat for UCS #######################

gamesUCS <- list()
plotsUCS <- list()
for (i in 1:length(gameList)) {
    gameNumber <- gameList[i]
    logName <- paste0("ucsLog", gameNumber, ".txt")
    gamesUCS[[i]] <- parseUCS(logName)
    gamesUCS[[i]]$Game <- gameNumber
    nam <- names(gamesUCS)
    nam[i] <- paste0("game", gameNumber)
    names(gamesUCS) <- nam
    plotsUCS[[i]] <- ggplot(data = gamesUCS[[i]], aes(y = Ucs, x = Day, colour = Agent)) + geom_line()
}

# Combine into one df for ggplot magic
allUCSData <- data.frame()
for (i in 1:length(games)) {
    allUCSData <- rbind(allUCSData, gamesUCS[[i]])
}
allUCSDataNAMM <- allUCSData %>% filter(as.numeric(Agent) == 5)


# GGplot magic
png("appdendix2.png", width = 1000, height = 600)
ggplot(data = allUCSData, aes(y = Ucs, x = Day, colour = Agent)) + 
    geom_line(alpha = 0.8) +
    geom_line(aes(x = Day, y = Ucs, colour = "black"), data = allUCSDataNAMM, colour = "black", size = 1.2) +
    facet_wrap( ~ Game, ncol=4, scales = "free_y") +
    theme_bw()
dev.off()

############### REPEAT FOR QUALITY ###########

gamesQ <- list()
plotsQ <- list()
for (i in 1:length(gameList)) {
    gameNumber <- gameList[i]
    logName <- paste0("QLog", gameNumber, ".txt")
    gamesQ[[i]] <- parseQ(logName)
    gamesQ[[i]]$Game <- gameNumber
    nam <- names(gamesQ)
    nam[i] <- paste0("game", gameNumber)
    names(gamesQ) <- nam
    plotsQ[[i]] <- ggplot(data = gamesQ[[i]], aes(y = Ucs, x = Day, colour = Agent)) + geom_line()
}

# Combine into one df for ggplot magic
allQData <- data.frame()
for (i in 1:length(gameList)) {
    allQData <- rbind(allQData, gamesQ[[i]])
}
allQDataNAMM <- allQData %>% filter(as.numeric(Agent) == 5)


# GGplot magic
png("appendix3.png", width = 1000, height = 600)
ggplot(data = allQData, aes(y = Q, x = Day, colour = Agent)) + 
    geom_line(alpha = 0.8) +
    geom_line(aes(x = Day, y = Q, colour = "black"), data = allQDataNAMM, colour = "black", size = 1.2) +
    facet_wrap( ~ Game, ncol=4, scales = "free_y") +
    theme_bw()
dev.off()

############### REPEAT FOR CAMPAIGNS ##############


gamesCamp <- list()
plotsCamp <- list()
for (i in 1:length(gameList)) {
    gameNumber <- gameList[i]
    gameNumber
    logName <- paste0("CampLog", gameNumber, ".txt")
    gamesCamp[[i]] <- parseCamp(logName)
    gamesCamp[[i]]$Game <- gameNumber
    nam <- names(gamesCamp)
    nam[i] <- paste0("game", gameNumber)
    names(gamesCamp) <- nam
    plotsCamp[[i]] <- 
        ggplot(data = gamesCamp[[i]], aes(y = TargetedImps + UntargetedImps, x = Day, colour = Agent, size = (TargetedImps + UntargetedImps) / Cost)) + 
        geom_point(alpha = 0.5) +
        geom_point(alpha = 0.5, colour = "black", data = gamesCamp[[i]] %>% filter(as.numeric(Agent) == 5) 
                   ,aes(x = Day, y = TargetedImps + UntargetedImps, size = (TargetedImps + UntargetedImps)/Cost))
    scale_size_continuous(range = c(2,6))
}



# Combine into one df for ggplot magic
allCampData <- data.frame()
for (i in 1:length(gamesCamp)) {
    allCampData <- rbind(allCampData, gamesCamp[[i]])
}
allCampData <- allCampData %>% mutate(Price = Cost / (TargetedImps + UntargetedImps))
allCampDataNAMM <- allCampData %>% filter(as.numeric(Agent) == 5)


# GGplot magic
png("appdendix4.png", width = 1000, height = 600)
ggplot(data = allCampData, aes(y = TargetedImps + UntargetedImps, x = Day, colour = Agent)) + 
    geom_point(alpha = 0.5) +
    geom_point(alpha = 0.5, colour = "black", data = allCampDataNAMM
               ,aes(x = Day, y = TargetedImps + UntargetedImps)) +
    theme_bw() +
    facet_wrap( ~ Game, ncol = 4, scales = "free_y")
dev.off()


#### EXTRA ANALYSIS
CampSummary <- allCampData %>% group_by(Agent) %>% summarise(Price = median(Price, na.rm = T))
png("PriceBar.png", width = 500, height = 400)
ggplot(data = CampSummary, aes(y = Price, x = reorder(Agent, -Price))) + 
    geom_bar(stat = "identity") +
    xlab("Agent") +
    ylab("Median price per impression") +
    theme_bw(base_size = 16) +
    theme(axis.text.x = element_text(angle = 90, hjust = 1, vjust = 0.5))
dev.off()

science_theme = theme(panel.grid.major = 
                          element_line(size = 0.5, color = "grey"), 
                      panel.grid.minor.y = element_blank(),
                      axis.line = 
                          element_line(size = 0.7, color = "black"), 
                      legend.position = c(0.9,0.5), 
                      text = element_text(size = 16))

gg1 <- ggplot(data = allCampData, aes(x = Price, fill = Agent)) +
    geom_histogram(bins = 100) + scale_x_log10(limits = c(1e-10,1e02)) +
    scale_fill_grey() +
    theme_bw() +
    science_theme +
    theme(legend.background = element_blank(),
          legend.text = element_text(size = 10),
          legend.key.size = unit(5, "mm"))

gg2 <- ggplot(data = allCampDataNAMM, aes(x = Price)) +
    geom_histogram(bins = 40) + 
    scale_x_log10(limits = c(1e-10,1e02)) +
    theme_bw() +
    science_theme

png("PriceHist.png", width = 1000, height = 800)
gridExtra::grid.arrange(gg1,gg2)
dev.off()


#####
BankByGame <- allBankData %>% filter(Day == 60) %>% group_by(Game)
Winners <- BankByGame %>% summarise(Best = Agent[which.max(Balance)])
allBankData <- merge(allBankData, Winners)
WinnerGames <- allBankData[allBankData$Agent == allBankData$Best,]
Output <- WinnerGames %>% group_by(Day) %>% summarise(Balance = mean(Balance))
Output$Type <- "Winners"

OutputNAMM <- allBankDataNAMM %>% group_by(Day) %>% summarise(Balance = mean(Balance))
OutputNAMM$Type <- "NAMM"

OutputMean <- allBankData %>% group_by(Day) %>% summarise(Balance = mean(Balance))
OutputMean$Type <- "Mean"

BalanceAvs <- rbind(Output, OutputNAMM, OutputMean)

png("AverageBalance", width = 1000, height = 800)
ggplot(Output, aes(x = Day, y = Balance, size = 2)) +
    geom_line(colour = "dark grey") +
    geom_line(data = OutputNAMM) +
    geom_line(data = OutputMean, colour = "light grey") +
    theme_bw() +
    science_theme +
    scale_size_identity()
dev.off()


BalanceAvplot <- ggplot(BalanceAvs, aes(x = Day, y = Balance, colour = Type, size = 2)) +
    geom_line() +
    theme_bw() +
    scale_size_identity() +
    scale_color_manual( "",
                          breaks = c("Winners", "NAMM", "Mean"),
                          values = c("Dark Grey", "Black", "Light Grey")) +
    science_theme +
    theme(legend.key.size = unit(8, "mm"))
        

####
QByGame <- allQData %>% filter(Day == 60) %>% group_by(Game)
Winners <- QByGame %>% summarise(Best = Agent[which.max(Q)])
allQData <- merge(allQData, Winners)
WinnerGames <- allQData[allQData$Agent == allQData$Best,]
Output <- WinnerGames %>% group_by(Day) %>% summarise(Q = mean(Q))
Output$Type <- "Winners"

OutputNAMM <- allQDataNAMM %>% group_by(Day) %>% summarise(Q = mean(Q))
OutputNAMM$Type <- "NAMM"

OutputMean <- allQData %>% group_by(Day) %>% summarise(Q = mean(Q))
OutputMean$Type <- "Mean"

QAvs <- rbind(Output, OutputNAMM, OutputMean)


QAvplot <- ggplot(QAvs, aes(x = Day, y = Q, colour = Type, size = 2)) +
    geom_line() +
    theme_bw() +
    scale_size_identity() +
    scale_color_manual( "",
                        breaks = c("Winners", "NAMM", "Mean"),
                        values = c("Dark Grey", "Black", "Light Grey")) +
    ylab("Quality") +
    theme(legend.key.size = unit(8, "mm")) +
    science_theme
        
gridExtra::grid.arrange(QAvplot, BalanceAvplot)
