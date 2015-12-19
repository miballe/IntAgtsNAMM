package soton.intagts;

import com.sun.org.omg.CORBA.ValueDefPackage.FullValueDescription;
import edu.umich.eecs.tac.props.Ad;
import edu.umich.eecs.tac.props.BankStatus;
import edu.umich.eecs.tac.props.Query;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

// Includes for file handling
import java.io.*;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.umich.eecs.tac.util.sampling.SynchronizedMutableSampler;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import se.sics.isl.transport.Transportable;
import se.sics.tasim.aw.Agent;
import se.sics.tasim.aw.Message;
import se.sics.tasim.props.SimulationStatus;
import se.sics.tasim.props.StartInfo;
import tau.tac.adx.ads.properties.AdType;
import tau.tac.adx.demand.Campaign;
import tau.tac.adx.demand.CampaignStats;
import tau.tac.adx.devices.Device;
import tau.tac.adx.props.*;
import tau.tac.adx.report.adn.AdNetworkKey;
import tau.tac.adx.report.adn.AdNetworkReport;
import tau.tac.adx.report.adn.AdNetworkReportEntry;
import tau.tac.adx.report.adn.MarketSegment;
import tau.tac.adx.report.demand.*;
import tau.tac.adx.report.demand.campaign.auction.CampaignAuctionReport;
import tau.tac.adx.report.publisher.AdxPublisherReport;
import tau.tac.adx.report.publisher.AdxPublisherReportEntry;
import tau.tac.adx.users.properties.Age;
import tau.tac.adx.users.properties.Gender;
import tau.tac.adx.users.properties.Income;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math3.stat.*;


/**
 * `
 * @author Mariano Schain
 * Test plug-in
 *
 */
public class AgentNAMM extends Agent {

	private final Logger log = Logger
			.getLogger(AgentNAMM.class.getName());

	/*
	 * Basic simulation information. An agent should receive the {@link
	 * StartInfo} at the beginning of the game or during recovery.
	 */
	@SuppressWarnings("unused")
	private StartInfo startInfo;


	/**
	 * Messages received:
	 *
	 * We keep all the {@link CampaignReport campaign reports} delivered to the
	 * agent. We also keep the initialization messages {@link PublisherCatalog}
	 * and {@link InitialCampaignMessage} and the most recent messages and
	 * reports {@link CampaignOpportunityMessage}, {@link CampaignReport}, and
	 * {@link AdNetworkDailyNotification}.
	 */
	private final Queue<CampaignReport> campaignReports;
	private PublisherCatalog publisherCatalog;
	private InitialCampaignMessage initialCampaignMessage;
	private AdNetworkDailyNotification adNetworkDailyNotification;

	/*
	 * The addresses of server entities to which the agent should send the daily
	 * bids data
	 */
	private String demandAgentAddress;
	private String adxAgentAddress;


	/*
	 * we maintain a list of queries - each characterized by the web site (the
	 * publisher), the device type, the ad type, and the user market segment
	 */
	private AdxQuery[] queries;

	/**
	 * Information regarding the latest campaign opportunity announced
	 */
	private CampaignData pendingCampaign;

	/**
	 * We maintain a collection (mapped by the campaign id) of the campaigns won
	 * by our agent.
	 */
	private Map<Integer, CampaignData> myCampaigns;

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Collection of campaigns thrown in the game
	//private List<CampaignData> campaignsInGame;
	private Map<Integer, CampaignData> campaignsInGame;
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	/*
	 * the bidBundle to be sent daily to the AdX
	 */
	private AdxBidBundle bidBundle;

	/*
	*  Perceptrons
	 */
	// TODO ALUN ALUN
	double ucsPerceptron;
	double ucsAlpha = 0.01;
	double profitablePerceptron;
	double profitAlpha = 50;
	double reachPerceptron = 1;
	double reachAlpha = 50;
	/*
	 * The current bid level for the user classification service
	 */
	double ucsBid;

	/*
	 * Defines the minimum fraction of learnt data coming from historic data over game data
	 */
	final double HISTORIC_FRAC = 0.5;

	/*
	 * The targeted service level for the user classification service
	 */
	int ucsTargetLevel;
	/*
	 * Track the quality of the previous day
	 */
	double quality = 1;
	/*
	 * Track performance data of whole game
	 * Currently unused
	 */
	private PerformanceData performanceData;
	/*
	 *  The bid campaign bid we send.
	 *  Note it is not reset each day on purpose so there is a default value in case we fail to calculate a bid in time.
	 */
	double cmpBid;
	double cmpBidPerImp = 0.8;

	/*
	 * current day of simulation
	 */
	private int day;
	private String[] publisherNames;
	private CampaignData currCampaign;

	/* Saving Historic Campaigns as global variable */
	historicCampaignData historicCampaigns = new historicCampaignData();


	/**
	 * This property is the instance of a new NAMM class to keep record of all Ad-Net reports during a game execution.
	 * The idea is to use historic data as reference to estimate new bid prices or provide values for some strategies.
	 * Basically this is storing entries returned during the AdNetworkReport keeping record if the last bids were
	 * successful, so it can be used as reference for later impresssion bids.
	 */
	private ImpressionHistory impressionBidHistory;

	public AgentNAMM() {
		campaignReports = new LinkedList<CampaignReport>();
		// Initializes impression bid history variable when a new Agent instance is created
		impressionBidHistory = new ImpressionHistory();
		//campaignsInGame = new ArrayList<CampaignData>();
	}

	/**
	 * Upon recieving a message from the server handle the information with the appropriate method
	 * @param message
	 */
	@Override
	protected void messageReceived(Message message) {
		try {
			Transportable content = message.getContent();

			// Dumps all received messages to log
			log.fine(message.getContent().getClass().toString());
			this.log.log(Level.ALL, message.getContent().getClass().toString());

			if (content instanceof InitialCampaignMessage) {
				handleInitialCampaignMessage((InitialCampaignMessage) content);
			} else if (content instanceof CampaignOpportunityMessage) {
				handleICampaignOpportunityMessage((CampaignOpportunityMessage) content);
			} else if (content instanceof CampaignReport) {
				handleCampaignReport((CampaignReport) content);
			} else if (content instanceof AdNetworkDailyNotification) {
				handleAdNetworkDailyNotification((AdNetworkDailyNotification) content);
			} else if (content instanceof AdxPublisherReport) {
				handleAdxPublisherReport((AdxPublisherReport) content);
			} else if (content instanceof SimulationStatus) {
				handleSimulationStatus((SimulationStatus) content);
			} else if (content instanceof PublisherCatalog) {
				handlePublisherCatalog((PublisherCatalog) content);
			} else if (content instanceof AdNetworkReport) {
				handleAdNetworkReport((AdNetworkReport) content);
			} else if (content instanceof StartInfo) {
				handleStartInfo((StartInfo) content);
			} else if (content instanceof BankStatus) {
				handleBankStatus((BankStatus) content);
			} else if(content instanceof CampaignAuctionReport) {
				hadnleCampaignAuctionReport((CampaignAuctionReport) content);
			}
			else {
				System.out.println("UNKNOWN Message Received: " + content);
			}

		} catch (NullPointerException e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
			this.log.log(Level.SEVERE,
					"Exception thrown while trying to parse message." + e);
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}

	private void hadnleCampaignAuctionReport(CampaignAuctionReport content) {
		// ingoring
	}

	private void handleBankStatus(BankStatus content) {
		System.out.println("Day " + day + ": " + content.toString());
	}

	/**
	 * Processes the start information.
	 *
	 * @param startInfo
	 *            the start information.
	 */
	protected void handleStartInfo(StartInfo startInfo) {
		this.startInfo = startInfo;
		System.out.println("Game Starting:" + startInfo);
	}

	/**
	 * Process the reported set of publishers
	 *
	 * @param publisherCatalog
	 */
	private void handlePublisherCatalog(PublisherCatalog publisherCatalog) {
		this.publisherCatalog = publisherCatalog;
		generateAdxQuerySpace();
		getPublishersNames();

	}

	/**
	 * On day 0, a campaign (the "initial campaign") is allocated to each
	 * competing agent. The campaign starts on day 1. The address of the
	 * server's AdxAgent (to which bid bundles are sent) and DemandAgent (to
	 * which bids regarding campaign opportunities may be sent in subsequent
	 * days) are also reported in the initial campaign message
	 */
	private void handleInitialCampaignMessage(
			InitialCampaignMessage campaignMessage) {
		System.out.println(campaignMessage.toString());

		day = 0;

		initialCampaignMessage = campaignMessage;
		demandAgentAddress = campaignMessage.getDemandAgentAddress();
		adxAgentAddress = campaignMessage.getAdxAgentAddress();

		CampaignData campaignData = new CampaignData(initialCampaignMessage);
		campaignData.setBudget(initialCampaignMessage.getBudgetMillis()/1000.0);
		currCampaign = campaignData;
		genCampaignQueries(currCampaign);

		// initialise performance data tracking
		performanceData = new PerformanceData();

		/*
		 * The initial campaign is already allocated to our agent so we add it
		 * to our allocated-campaigns list.
		 */
		System.out.println("Day " + day + ": Allocated campaign - " + campaignData);
		myCampaigns.put(initialCampaignMessage.getId(), campaignData);

		/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		campaignsInGame.put(initialCampaignMessage.getId(), campaignData);
		////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		// Load historic campaigns into a list
		String workingDir = System.getProperty("user.dir");
		System.out.println("Loading Historic Campaigns...");
		historicCampaigns.loadDataFromFile(workingDir + "/cmpLog.csv");
		System.out.println("Number of Campaigns loaded:" + historicCampaigns.getNumberOfRecords());
	}

	/**
	 * On day n ( > 0) a campaign opportunity is announced to the competing
	 * agents. The campaign starts on day n + 2 or later and the agents may send
	 * (on day n) related bids (attempting to win the campaign). The allocation
	 * (the winner) is announced to the competing agents during day n + 1.
	 */
	private void handleICampaignOpportunityMessage(
			CampaignOpportunityMessage com) {
		day = com.getDay();
		////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		//campaignsInGame.add(pendingCampaign);
		////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		// For campaigns that finished yesterday set performance metrics.
		for (Map.Entry<Integer, CampaignData> entry : myCampaigns.entrySet()) {
			CampaignData campaign = entry.getValue();
			if ((entry.getValue().dayEnd == day - 1)) {
				System.out.println("...");
				long imps = (long)(campaign.stats.getOtherImps() + campaign.stats.getTargetedImps());
				double revenue = campaign.budget * ERRcalc(campaign, imps);
				// Update ended campaign
				campaign.update(revenue);

				// Update performance data
				performanceData.updateData(campaign);
				// TODO: ALUN ALUN
				// update perceptrons
				//profitablePerceptron *= profitAlpha*(campaign.revenue - campaign.stats.getCost() - 0.2);
				//reachPerceptron += reachAlpha*campaign.reachFulfillment;
				profitablePerceptron = 1;
				reachPerceptron = (1 + ((1 - campaign.impTargetFulfillment) / 10));
				if(reachPerceptron > 2){
					reachPerceptron = 2;
				}
				else if (reachPerceptron < 0.5) {
					reachPerceptron = 0.5;
				}
				else{
					reachPerceptron = 1;
				}
				ucsPerceptron += ucsAlpha*campaign.ucsCost;

				System.out.println("### PERCEPTRON - ProfPerceptron: " + profitablePerceptron + ", reachPerceptron:" + reachPerceptron + ", UCSPerceptron:" + ucsPerceptron);

				// Print relevant performance statistics
				System.out.printf(
						"Day %d: Campaign(%d) Completed________________________________\n" +
								"    Day Start:%d End:%d Duration:%d days \n" +
								"    Reach:%d (per day:%.2f) Impression Target:%d \n" +
								"    Impressions:%d Targeted:%d Untargeted:%d \n" +
								"    Target Fulfillment:%d%% Reach Fulfillment:%d%% \n" +
								"    Revenue:%.3f Budget:%.3f Bid:%.3f \n" +
								"    Bid:2nd Ratio: %.2f \n" +
								"    Impression Cost:%.2f Estimate:%.2f Accuracy:%d%% \n" +
								"    UCS Cost:%.2f Estimated:%.2f Accuracy:%d%% \n" + /* UCS cost estimate approximates with non-overlapping campaigns */
								"    Profit:%.2f  (Per Impression, millis:%d) \n" +   /* Above gives underestimate for profit */
								"    Profit: Estimated:%.2f Accuracy:%d%% | uncorrected:%.2f Accuracy:%d%%)\n" +
								"    Quality Change:%.2f Estimate:%.2f Accuracy:%d%% \n",
						day, campaign.id,
						campaign.dayStart, campaign.dayEnd, campaign.dayEnd - campaign.dayStart,
						campaign.reachImps, (double)(campaign.reachImps / (campaign.dayEnd - campaign.dayStart)), campaign.impressionTarget,
						(long)(campaign.stats.getTargetedImps() + campaign.stats.getOtherImps()),
						(long)campaign.stats.getTargetedImps(), (long)campaign.stats.getOtherImps(),
						(long)(campaign.impTargetFulfillment*100), (long)(campaign.reachFulfillment*100),
						campaign.revenue, campaign.budget, campaign.cmpBid,
						campaign.bidVs2ndRatio,
						campaign.stats.getCost(), campaign.estImpCost ,(long)(campaign.estCostAcc*100),
						campaign.ucsCost, campaign.estUcsCost, (long)(campaign.estUcsCostAcc*100),
						campaign.profit, (long)((campaign.profit/(campaign.stats.getOtherImps() + campaign.stats.getTargetedImps()))*1000),
						campaign.profitEstimate, (long)(campaign.estProfitAcc*100), campaign.uncorrectedProfitEstimate,
						(long)(campaign.uncorrectedProfitAcc*100),
						campaign.qualityChange, campaign.estQualityChange, (long)(campaign.estQualityChangeAcc*100));

				/* Currently not properly implemented game overview
				System.out.printf(
					"Day %d: Performance Report (%d Campaigns complete)_____________________________\n" +
					"    Revenue:%.3f \n" +
					"    Profit:%.3f (per Imp(millis):%.3f) Estimated profit accuracy:%.3f (uncorrected:%.3f)\n" +
					"    bid vs 2nd price ratio: %.2f \n" +
					"    Estimated cost accuracy: %d%% (impressions:%d%%. Ucs:%d%%) \n" +
					"    Impression Target Fulfillment:%d%% Reach Fulfillment:%d%% \n",
					day,performanceData.numCamps,
					performanceData.revenue,
					performanceData.profit, performanceData.profitPerImpression*1000, performanceData.estProfitAcc, performanceData.uncorrectedProfitEstimateAcc,
					performanceData.avBidVs2ndRatio,
					(long)(performanceData.estCostAcc*100), (long)(performanceData.estImpCostAcc*100), (long)(performanceData.estUcsCostAcc*100),
					(long)(performanceData.impTargetFulfillment*100), (long)(performanceData.reachFulfillment*100)
					);*/
			}
		}




		/**
		 * React to new campaign opportunity message by choosing an appropriate bidding strategy
		 * evaluating and sending both campaign and ucs bids
		 */
		pendingCampaign = new CampaignData(com);
		System.out.println("Day " + day + ": Campaign opportunity" + pendingCampaign);

		ImpressionCostEstimator();

		// TODO ALUN ALUN
		ucsPerceptron += ucsAlpha*(0.8-adNetworkDailyNotification.getServiceLevel())/0.8;


		System.out.println(" ~~ UCS perceptron: " + ucsPerceptron + " Reach perceptron: " + reachPerceptron + " Profitable Perceptron: " + profitablePerceptron);
		long cmpimps = com.getReachImps();
		int startDays = 5;
		// Starting strategy for first few days
		if (day <= startDays) {
			cmpBid = campaignStartingStrategy();
		}
		// Quality recovery when quality is too low
		else if (adNetworkDailyNotification.getQualityScore() < 1) { // Condition for Quality strategy
			cmpBid = campaignQualityRecoveryStrategy();
		}
		else cmpBid = campaignProfitStrategy();
		System.out.println("Day " + day + ": Campaign - Bid: " + (long)(cmpBid*1000));
		// If bid is too high, just bid the maximum value.
		if (cmpBid >= bidTooHigh(cmpimps, 95)) {
			cmpBid = 0.001 * cmpimps * adNetworkDailyNotification.getQualityScore() - 0.001;
			System.out.print(" " + (long)(cmpBid*1000) + "-too high!");
		}
		// If bid is too low, bid the "minimum value"
		double lowBid = bidTooLow(cmpimps, 30);
		if (cmpBid <= lowBid) {
			cmpBid = lowBid + 0.001;
			System.out.println(" " + (long)(cmpBid*1000) + "-too low!");
		}
		/*
		 * The campaign requires com.getReachImps() impressions. The competing
		 * Ad Networks bid for the total campaign Budget (that is, the ad
		 * network that offers the lowest budget gets the campaign allocated).
		 * The advertiser is willing to pay the AdNetwork at most 1$ CPM,
		 * therefore the total number of impressions may be treated as a reserve
		 * (upper bound) price for the auction.
		 */

		/*
		 * Adjust ucs bid s.t. target level is achieved. Note: The bid for the
		 * user classification service is piggybacked
		 */
		// TODO: Nikola UCS bid calculation here
		// Random random = new Random();

		if (adNetworkDailyNotification != null) {
			double ucsLevel = adNetworkDailyNotification.getServiceLevel();
			//ucsBid = 0.1 + random.nextDouble()/10.0;
			ucsBid = ucsBidCalculator(1);
			System.out.println("Day " + day + ": ucs level reported: " + ucsLevel);
		} else {
			System.out.println("Day " + day + ": Initial ucs bid is " + ucsBid);
		}

		/* Note: Campaign bid is in millis */
		System.out.println("Day " + day + ": Submitting Campaign bid (millis): " + (long)(cmpBid*1000));
		System.out.println("Day " + day + ": Submitting UCS service bid: " + ucsBid);
		AdNetBidMessage bids = new AdNetBidMessage(ucsBid, pendingCampaign.id, (long)(cmpBid*1000));
		sendMessage(demandAgentAddress, bids);
		/* TODO ALUN: Fix bug where day 0 isn't bid for
		 *	- Harder than expected the error moves position on the first day of each run
		 */

	}

	/**
	 * On day n ( > 0), the result of the UserClassificationService and Campaign
	 * auctions (for which the competing agents sent bids during day n -1) are
	 * reported. The reported Campaign starts in day n+1 or later and the user
	 * classification service level is applicable starting from day n+1.
	 */
	private void handleAdNetworkDailyNotification(
			AdNetworkDailyNotification notificationMessage) {

		////////////////////////////////////////////////////////////////////////////////////////////////////////////
		campaignsInGame.put(pendingCampaign.id, pendingCampaign);
		/////////////////////////////////////////////////////////////////////////////////////////////////////////

		adNetworkDailyNotification = notificationMessage;
		System.out.println("Day " + day + ": Daily notification for campaign "
				+ adNetworkDailyNotification.getCampaignId());

		String campaignAllocatedTo = " allocated to "
				+ notificationMessage.getWinner();

		if ((pendingCampaign.id == adNetworkDailyNotification.getCampaignId())
				&& (notificationMessage.getCostMillis() != 0)) {

			/* add campaign to list of won campaigns */
			pendingCampaign.setBudget(notificationMessage.getCostMillis() / 1000.0);
			pendingCampaign.setBid(cmpBid);
			pendingCampaign.setBidVs2ndRatio();
			currCampaign = pendingCampaign;
			genCampaignQueries(currCampaign);
			// Test for impressionTarget function
			pendingCampaign.setImpressionTargets();
			myCampaigns.put(pendingCampaign.id, pendingCampaign);

			campaignAllocatedTo = " WON at cost (Millis)"
					+ notificationMessage.getCostMillis();


		}

		System.out.println("Day " + day + ": " + campaignAllocatedTo
				+ ". UCS Level set to " + notificationMessage.getServiceLevel()
				+ " at price " + notificationMessage.getPrice()
				+ " Quality Score is: " + notificationMessage.getQualityScore());

		// Attribute the ucs cost to any running campaigns.
		int ongoingCamps = 0;
		// count the number of ongoing campaigns
		for (Map.Entry<Integer, CampaignData> entry : myCampaigns.entrySet()) {
			CampaignData campaign = entry.getValue();
			if( (day <= campaign.dayEnd) && (day >= campaign.dayStart)){
				ongoingCamps ++;
			}
		}
		// send each campaign an even split of ucs cost
		for (Map.Entry<Integer, CampaignData> entry : myCampaigns.entrySet()) {
			CampaignData campaign = entry.getValue();
			if( (day <= campaign.dayEnd) && (day >= campaign.dayStart)){
				campaign.ucsCost += notificationMessage.getPrice() / ongoingCamps;
			}
		}


	}

	/**
	 * The SimulationStatus message received on day n indicates that the
	 * calculation time is up and the agent is requested to send its bid bundle
	 * to the AdX.
	 */
	private void handleSimulationStatus(SimulationStatus simulationStatus) {
		System.out.println("Day " + day + " : Simulation Status Received");
		System.out.println("###SIMSTAT### " + simulationStatus.toString());
		sendBidAndAds();
		System.out.println("Day " + day + " ended. Starting next day");
		++day;
	}

	/**
	 * Miguel
	 * Wrote additional code to the existing one to apply the impression bid strategy.
	 * Basically the strategy consist of getting a history of previous bids regardless of whether were successful or not
	 * and define the new bid (bid bundle) according to the best estimated price. As satarting point, the algorithm is using
	 * a baseline gathered from test games with dummy agents and previous version of this agent.
	 */
	protected void sendBidAndAds() {

		/**
		 * Block below was not used. Instead, other blocks were used to store the AdNet report.
		 */
		// FileWriter csvWriter;
		// try{
		// csvWriter = new FileWriter("c:\\temp\\queries.csv");
		// StringBuilder csvLine = new StringBuilder();

		bidBundle = new AdxBidBundle();
		int dayBiddingFor = day + 1;
		double rbid = 10000.0;

		/**
		 * add bid entries w.r.t. each active campaign with remaining contracted cmpBidMillis
		 * impressions.
		 *
		 * for now, a single entry per active campaign is added for queries of
		 * matching target segment.
		 */

		if ((dayBiddingFor >= currCampaign.dayStart)
				&& (dayBiddingFor <= currCampaign.dayEnd)
				&& (currCampaign.impsTogo() > 0)) {

			int entCount = 0;
			int qryCount = 0;

			/**
			 * TODO: MB, Consider overachieving campaigns when quality < 1. This was attempted by using the history bid file.
			 */
			for (AdxQuery query : currCampaign.campaignQueries) {
				if (currCampaign.impsTogo() - entCount > 0) {
					//System.out.println("###QUERY### " + query.toString());
					/**
					 * among matching entries with the same campaign id, the AdX
					 * randomly chooses an entry according to the designated
					 * weight. by setting a constant weight 1, we create a
					 * uniform probability over active campaigns(irrelevant because we are bidding only on one campaign)
					 */
					if (query.getDevice() == Device.pc) {
						if (query.getAdType() == AdType.text) {
							entCount++;
						} else {
							entCount += currCampaign.videoCoef;
						}
					} else {
						if (query.getAdType() == AdType.text) {
							entCount+=currCampaign.mobileCoef;
						} else {
							entCount += currCampaign.videoCoef + currCampaign.mobileCoef;
						}
					}
					/*
					// Lookup past campaigns
					// if unprofitable bid lower
					// if reach ratio < 1 bid higher.
					double numProfit = 0;
					double numUnprofit = 0;
					for (Map.Entry<Integer, CampaignData> entry : myCampaigns.entrySet()) {
						CampaignData campaign = entry.getValue();
						if (campaign.dayEnd < day){
							if (campaign.profit > 0) { numProfit++;}
							else numUnprofit++;
						}
					}
					double proportionprofit = numProfit/numUnprofit;

					// Lookup past campaigns
					// if unprofitable bid lower
					// if reach ratio < 1 bid higher.
					double reachMet = 0;
					double reachUnmet = 0;
					for (Map.Entry<Integer, CampaignData> entry : myCampaigns.entrySet()) {
						CampaignData campaign = entry.getValue();
						if (campaign.dayEnd < day){
							if (campaign.profit > 0) { numProfit++;}
							else numUnprofit++;
						}
					}
					double proportionReachMet = reachMet/reachUnmet; */



					for (Map.Entry<Integer, CampaignData> entry : myCampaigns.entrySet()) {
						CampaignData campaign = entry.getValue();
						if ((campaign.dayStart <= day) & (campaign.dayEnd >= day)) {
							// This code was changed during the competition. Originally rbid was assigned the outcome of
							// ImpressionBidCalculator that actually reads from the bid history file to determine a reasonable price
							// During the competition this was changing too often and calculated prices were not the most accurate.
							double rbidOld = ImpressionBidCalculator(entCount - qryCount, query);
							// During the competition this was changed to get an initial value similar to the inherited agent
							// and then adjusted some parameters to have higher chances to win more impression bids.
							// Part of this implementation was the creation of an algorithm similar to Perceptron.
							rbid = campaign.impCostEstThisDay * 500;
							// TODO: ALUN ALUN
							rbid = rbid * (reachPerceptron * profitablePerceptron);
							System.out.println("old " + rbidOld + " new " + rbid);
							bidBundle.addQuery(query, rbid, new Ad(null), campaign.id, 1);
							System.out.println("#####SENDBIDANDADS##### BidVal:" + rbid + " PPM:" + rbid / (entCount - qryCount) + ", IMPCOSTEST:" + campaign.impCostEstThisDay);

							double impressionLimit = campaign.impsTogo();
							double budgetLimit = campaign.budget;
							bidBundle.setCampaignDailyLimit(campaign.id,
									(int) impressionLimit, budgetLimit);
						}
					}
					//System.out.println("###QUERY### " + query.toString() + ", CampaingId: " + currCampaign.id);
					qryCount = entCount;
				}
			}


			System.out.println("Day " + day + " Bid Bundle: Updated " + entCount
					+ " Bid Bundle entries for Campaign id " + currCampaign.id);
			log.log(Level.ALL, "## Bid Bundle ##; currCampaign: " + currCampaign.id + "; " + (long)currCampaign.budget);
		}

		if (bidBundle != null) {
			System.out.println("Day " + day + ": Sending BidBundle");
			sendMessage(adxAgentAddress, bidBundle);

			/*for (Map.Entry<Integer, CampaignData> campaign : myCampaigns.entrySet()) {
				System.out.println("-----------------------------------------------------------------------------------------------------------------");
				System.out.println("CAMPAIGN" + campaign.getValue().id + "-->"+ "reachImps = "+campaign.getValue().reachImps +";  dayStart = " + campaign.getValue().dayStart + ";  dayEnd = "+ campaign.getValue().dayEnd + ";  TargetSegmentSize = " + MarketSegment.usersInMarketSegments().get(campaign.getValue().targetSegment));//  campaign.getValue().targetSegment.hashCode() );
				System.out.println("----CAMPAIGN" + campaign.getValue().id + "-->  Popularity:" + campaign.getValue().popInSegmentOfOurCampaign + ". ReservePrice Estimated:" + campaign.getValue().ReservePriceEstimated + ", ReservePrice Today" + campaign.getValue().ReservePriceThisDay + ". IMPRESSION COST ESTIMATE TODAY:" + campaign.getValue().impCostEstThisDay + "------");
				System.out.println("-----------------------------------------------------------------------------------------------------------------");
			}*/


		}
	}

	/**
	 * Campaigns performance w.r.t. each allocated campaign
	 */
	private void handleCampaignReport(CampaignReport campaignReport) {

		campaignReports.add(campaignReport);
		/*
		 * for each campaign, the accumulated statistics from day 1 up to day
		 * n-1 are reported
		 */
		for (CampaignReportKey campaignKey : campaignReport.keys()) {
			int cmpId = campaignKey.getCampaignId();
			CampaignStats cstats = campaignReport.getCampaignReportEntry(
					campaignKey).getCampaignStats();
			myCampaigns.get(cmpId).setStats(cstats);

			System.out.println("Day " + day + ": Updating campaign " + cmpId + " stats: "
					+ cstats.getTargetedImps() + " tgtImps "
					+ cstats.getOtherImps() + " nonTgtImps. Cost of imps is "
					+ cstats.getCost());
		}
	}
	/**
	 * Users and Publishers statistics: popularity and ad type orientation
	 */
	private void handleAdxPublisherReport(AdxPublisherReport adxPublisherReport) {
		System.out.println("Publishers Report: ");
		for (PublisherCatalogEntry publisherKey : adxPublisherReport.keys()) {
			AdxPublisherReportEntry entry = adxPublisherReport
					.getEntry(publisherKey);
			System.out.println(entry.toString());
		}
	}

	/**
	 *
	 * @param //AdNetworkReport
	 */
	private void handleAdNetworkReport(AdNetworkReport adnetReport) {
		AdNetworkReportEntry repEntry;
		System.out.println("Day " + day + " : AdNetworkReport:   ");
		for (AdNetworkKey adKey : adnetReport.keys()) {
			repEntry = adnetReport.getEntry(adKey);
			if(repEntry.getCost() > 0.0001) {
				impressionBidHistory.impressionList.add(new ImpressionRecord(repEntry));
			}
		}
		System.out.println("#####BIDIMPRHISTORY##### NItems " + impressionBidHistory.impressionList.size() +
				"\n   ### Male stats: " + impressionBidHistory.getStatsPerSegment(MarketSegment.MALE, null, null).toString() +
				"\n   ### Female-HighIncome stats: " + impressionBidHistory.getStatsPerSegment(MarketSegment.FEMALE, null, MarketSegment.HIGH_INCOME).toString());
	}

	@Override
	protected void simulationSetup() {

		day = 0;
		bidBundle = new AdxBidBundle();

		/* initial bid between 0.1 and 0.2 */
		ucsBid = 0.2;

		myCampaigns = new HashMap<Integer, CampaignData>();
		campaignsInGame = new HashMap<Integer, CampaignData>();
		log.fine("AdNet " + getName() + " simulationSetup");

		// As soon as the simulation starts, the first action is to read the impression bid history to have
		// a baseline for game bids.
		impressionBidHistory.loadFile();
	}

	@Override
	protected void simulationFinished() {
		// Saves the impression bid history file with updates from the current game
		impressionBidHistory.saveFile();
		
		campaignSaveFile();
		campaignReports.clear();
		bidBundle = null;
	}

	/**
	 * A user visit to a publisher's web-site results in an impression
	 * opportunity (a query) that is characterized by the the publisher, the
	 * market segment the user may belongs to, the device used (mobile or
	 * desktop) and the ad type (text or video).
	 *
	 * An array of all possible queries is generated here, based on the
	 * publisher names reported at game initialization in the publishers catalog
	 * message
	 */
	private void generateAdxQuerySpace() {
		if (publisherCatalog != null && queries == null) {
			Set<AdxQuery> querySet = new HashSet<AdxQuery>();

			/*
			 * for each web site (publisher) we generate all possible variations
			 * of device type, ad type, and user market segment
			 */
			for (PublisherCatalogEntry publisherCatalogEntry : publisherCatalog) {
				String publishersName = publisherCatalogEntry
						.getPublisherName();
				for (MarketSegment userSegment : MarketSegment.values()) {
					Set<MarketSegment> singleMarketSegment = new HashSet<MarketSegment>();
					singleMarketSegment.add(userSegment);

					querySet.add(new AdxQuery(publishersName,
							singleMarketSegment, Device.mobile, AdType.text));

					querySet.add(new AdxQuery(publishersName,
							singleMarketSegment, Device.pc, AdType.text));

					querySet.add(new AdxQuery(publishersName,
							singleMarketSegment, Device.mobile, AdType.video));

					querySet.add(new AdxQuery(publishersName,
							singleMarketSegment, Device.pc, AdType.video));

				}

				/**
				 * An empty segments set is used to indicate the "UNKNOWN"
				 * segment such queries are matched when the UCS fails to
				 * recover the user's segments.
				 */
				querySet.add(new AdxQuery(publishersName,
						new HashSet<MarketSegment>(), Device.mobile,
						AdType.video));
				querySet.add(new AdxQuery(publishersName,
						new HashSet<MarketSegment>(), Device.mobile,
						AdType.text));
				querySet.add(new AdxQuery(publishersName,
						new HashSet<MarketSegment>(), Device.pc, AdType.video));
				querySet.add(new AdxQuery(publishersName,
						new HashSet<MarketSegment>(), Device.pc, AdType.text));
			}
			queries = new AdxQuery[querySet.size()];
			querySet.toArray(queries);
		}
	}

	/*generates an array of the publishers names
	 * */
	private void getPublishersNames() {
		if (null == publisherNames && publisherCatalog != null) {
			ArrayList<String> names = new ArrayList<String>();
			for (PublisherCatalogEntry pce : publisherCatalog) {
				names.add(pce.getPublisherName());
			}

			publisherNames = new String[names.size()];
			names.toArray(publisherNames);
		}
	}
	/*
	 * generates the campaign queries relevant for the specific campaign, and assign them as the campaigns campaignQueries field
	 */
	private void genCampaignQueries(CampaignData campaignData) {
		Set<AdxQuery> campaignQueriesSet = new HashSet<AdxQuery>();
		for (String PublisherName : publisherNames) {
			campaignQueriesSet.add(new AdxQuery(PublisherName,
					campaignData.targetSegment, Device.mobile, AdType.text));
			campaignQueriesSet.add(new AdxQuery(PublisherName,
					campaignData.targetSegment, Device.mobile, AdType.video));
			campaignQueriesSet.add(new AdxQuery(PublisherName,
					campaignData.targetSegment, Device.pc, AdType.text));
			campaignQueriesSet.add(new AdxQuery(PublisherName,
					campaignData.targetSegment, Device.pc, AdType.video));
		}

		campaignData.campaignQueries = new AdxQuery[campaignQueriesSet.size()];
		campaignQueriesSet.toArray(campaignData.campaignQueries);
		//System.out.println("!!!!!!!!!!!!!!!!!!!!!!"+Arrays.toString(campaignData.campaignQueries)+"!!!!!!!!!!!!!!!!");
	}

	/**
	 * Definition of class Campaign Data which stores all variables and statistics associated with campaigns.
	 */
	private class CampaignData {
		/* campaign attributes as set by server */
		int game;
		Long reachImps;
		long dayStart;
		long dayEnd;
		Set<MarketSegment> targetSegment;
		double videoCoef;
		double mobileCoef;
		int id;
		private AdxQuery[] campaignQueries;//array of queries relevant for the campaign.

		/* campaign info as reported */
		CampaignStats stats; // targeted imps, other imps and cost of imps
		double budget;
		double revenue;
		double profitEstimate;
		double cmpBid;
		long impressionTarget;
		double uncorrectedProfitEstimate;
		double costEstimate;
		double estImpCost;
		double estUcsCost;
		double qualityChange;
		double estQualityChange;
		double ucsCost;

		/* Performance data */
		double estCostAcc;
		double estProfitAcc;
		double uncorrectedProfitAcc;
		double estQualityChangeAcc;
		double impTargetFulfillment;
		double bidVs2ndRatio;
		double profit;
		double profitPerImpression;
		double reachFulfillment;
		double estUcsCostAcc;

		double popInSegmentOfOurCampaign;
		double impCostAvg;
		double ReservePriceEstimated;
		double ReservePriceThisDay;
		double impressionCostEstimate;
		double impCostEstThisDay;

		public CampaignData(InitialCampaignMessage icm) {
			reachImps = icm.getReachImps();
			dayStart = icm.getDayStart();
			dayEnd = icm.getDayEnd();
			targetSegment = icm.getTargetSegment();
			videoCoef = icm.getVideoCoef();
			mobileCoef = icm.getMobileCoef();
			id = icm.getId();
			stats = new CampaignStats(0, 0, 0);
			budget = 0.0;
			impressionTarget = reachImps;
			revenue = 0;
			profit = 0.0;
			ucsCost = 0;
			profitEstimate = 0.0;
			uncorrectedProfitEstimate = 0.0;
			costEstimate = 0.0;
			estCostAcc = 0.0;
			estProfitAcc = 0.0;
			uncorrectedProfitAcc = 0.0;
			impTargetFulfillment = 0.0;
			estUcsCostAcc = 0.0;
			bidVs2ndRatio = 0.0;
			profit = 0.0;
			profitPerImpression = 0.0;
			reachFulfillment = 0.0;
			estImpCost = 0.0;
			estUcsCost = 0.0;
			qualityChange = 0.0;
			estQualityChange = 0.0;
			estQualityChangeAcc = 0.0;
			popInSegmentOfOurCampaign = 0;
			impCostAvg = 0;
			ReservePriceEstimated = 0;
			ReservePriceThisDay = 0;
			impCostEstThisDay = 0;
			impressionCostEstimate = 0;
			game = startInfo.getSimulationID();
		}
		public CampaignData(int game, Long reachImps, long dayStart, long dayEnd, Set<MarketSegment> targetSegment,
							double videoCoef, double mobileCoef, int id, AdxQuery[] campaignQueries,
							CampaignStats cstats, double budget, double revenue, double profitEstimate,
							double cmpBid, long impressionTarget, double uncorrectedProfitEstimate,
							double costEstimate, double estImpCost, double estUcsCost, double qualityChange,
							double estQualityChange, double ucsCost, double estCostAcc, double estProfitAcc,
							double uncorrectedProfitAcc, double estQualityChangeAcc, double impTargetFulfillment,
							double bidVs2ndRatio, double profit, double profitPerImpression, double reachFulfillment,
							double estUcsCostAcc) {
			this.game = game;
			this.reachImps = reachImps;
			this.dayStart = dayStart;
			this.dayEnd = dayEnd;
			this.targetSegment = targetSegment;
			this.videoCoef = videoCoef;
			this.mobileCoef = mobileCoef;
			this.id = id;
			this.campaignQueries = campaignQueries;
			this.stats = cstats;
			this.budget = budget;
			this.revenue = revenue;
			this.profitEstimate = profitEstimate;
			this.cmpBid = cmpBid;
			this.impressionTarget = impressionTarget;
			this.uncorrectedProfitEstimate = uncorrectedProfitEstimate;
			this.costEstimate = costEstimate;
			this.estImpCost = estImpCost;
			this.estUcsCost = estUcsCost;
			this.qualityChange = qualityChange;
			this.estQualityChange = estQualityChange;
			this.ucsCost = ucsCost;
			this.estCostAcc = estCostAcc;
			this.estProfitAcc = estProfitAcc;
			this.uncorrectedProfitAcc = uncorrectedProfitAcc;
			this.estQualityChangeAcc = estQualityChangeAcc;
			this.impTargetFulfillment = impTargetFulfillment;
			this.bidVs2ndRatio = bidVs2ndRatio;
			this.profit = profit;
			this.profitPerImpression = profitPerImpression;
			this.reachFulfillment = reachFulfillment;
			this.estUcsCostAcc = estUcsCostAcc;
		}
		public CampaignData(CampaignOpportunityMessage com) {
			dayStart = com.getDayStart();
			dayEnd = com.getDayEnd();
			id = com.getId();
			reachImps = com.getReachImps();
			targetSegment = com.getTargetSegment();
			mobileCoef = com.getMobileCoef();
			videoCoef = com.getVideoCoef();
			stats = new CampaignStats(0, 0, 0);
			budget = 0.0;
			cmpBid = 0.0;
			estUcsCostAcc = 0.0;
			impressionTarget = reachImps;
			revenue = 0;
			profit = 0.0;
			profitEstimate = 0.0;
			uncorrectedProfitEstimate = 0.0;
			costEstimate = 0.0;
			reachFulfillment = 0.0;
			estImpCost = 0.0;
			qualityChange = 0.0;
			estUcsCost = 0.0;
			estQualityChange = 0.0;
			ucsCost = 0;
			estQualityChangeAcc = 0.0;
			game = startInfo.getSimulationID();

			popInSegmentOfOurCampaign = 0;
			impCostAvg = 0;
			ReservePriceEstimated = 0;
			ReservePriceThisDay = 0;
			impCostEstThisDay = 0;
			impressionCostEstimate = 0;
		}

		// updates campaign statistics after it has ended
		public void update(double revenue) {
			this.setRevenue(revenue);
			this.setProfit();
			this.setEstCostAcc();
			this.setUncorrectedProfitAcc();
			this.setEstProfitAcc();
			this.setImpTargetFulfillment();
			this.setProfitPerImpression();
			this.setReachFulfillment();
			this.setBidVs2ndRatio();
			this.setQualityChange();
			this.setEstQualityChangeAcc();
			this.setEstUcsCostAcc();
		}

		// Setters
		// TODO ALUN: debug setters
		public void setQualityChange() {
			// Detects change in quality score from yesterday,
			// attributes change equally to all campaigns ended in that time
			System.out.println("Quality:" + adNetworkDailyNotification.getQualityScore() + "yesterday's quality" + quality
					+ "estimated quality change:" + this.estQualityChange);
			int count=0;
			for (Map.Entry<Integer, CampaignData> entry : myCampaigns.entrySet()) {
				long end = entry.getValue().dayEnd;
				if (end == day - 1) {count++;}
			}
			qualityChange = (adNetworkDailyNotification.getQualityScore() - quality)/count;
			quality = adNetworkDailyNotification.getQualityScore();

		}
		public void setEstQualityChangeAcc() {
			estQualityChangeAcc = estQualityChange / qualityChange;
		}
		public void setEstUcsCostAcc() {
			estUcsCostAcc = estUcsCost / ucsCost;
		}
		public void setBudget(double d) { budget = d; }
		public void setRevenue(double r) { revenue = r; }
		public void setBid(double b) { cmpBid = b; }
		public void setEstCostAcc(){
			estCostAcc = estImpCost / stats.getCost();
		}
		public void setEstProfitAcc(){
			estProfitAcc = (profitEstimate) / profit;
		}
		public void setUncorrectedProfitAcc(){
			uncorrectedProfitAcc = (uncorrectedProfitEstimate) / profit;
		}
		public void setReachFulfillment(){
			reachFulfillment = (stats.getTargetedImps() + stats.getOtherImps()) / reachImps;
		}
		public void setImpTargetFulfillment(){
			impTargetFulfillment = (stats.getTargetedImps() + stats.getOtherImps()) / impressionTarget;
		}
		public void setBidVs2ndRatio(){
			bidVs2ndRatio = this.cmpBid * adNetworkDailyNotification.getQualityScore() / budget;
		}
		public void setProfit(){
			profit = revenue - stats.getCost();
		}
		public void setProfitPerImpression(){
			profitPerImpression = profit / (stats.getTargetedImps() + stats.getOtherImps());
		}

		@Override
		// toString returns only the server stored statistics on a campaign
		public String toString() {
			return "Campaign ID " + id + ": " + "day " + dayStart + " to "
					+ dayEnd + " " + targetSegment + ", reach: " + reachImps
					+ " coefs: (v=" + videoCoef + ", m=" + mobileCoef + ")";
		}
		// ToWrite returns all the stats for writing to CSV files
		public String toWrite() {
			return startInfo.getSimulationID() + "," + id + "," + dayStart + "," + dayEnd + "," + reachImps + ","
					+ targetSegment.toString().replace(',',':') + "," + videoCoef + ","
					+ mobileCoef + "," + stats.getCost() + "," + stats.getTargetedImps() + "," + stats.getOtherImps() + ","
					+ budget + "," + revenue  + "," + profitEstimate  + "," + cmpBid + "," + impressionTarget  + "," +
					uncorrectedProfitEstimate + "," + costEstimate  + "," + estImpCost  + "," +
					estUcsCost  + "," + qualityChange  + "," + estQualityChange  + "," + ucsCost  + "," + estCostAcc
					+ "," +estProfitAcc  + "," + uncorrectedProfitAcc + "," + estQualityChangeAcc + "," + impTargetFulfillment
					+ "," + bidVs2ndRatio + "," + profit + "," + profitPerImpression + "," + reachFulfillment  + "," +
					estUcsCostAcc;
		}

		//CSV file header
		final String FILE_HEADER = "id,dayStart,dayEnd,reachImps,targetSegment,videoCoef,mobileCoef," +
				"adxCost,targetedImps,untargetedImps,budget,revenue,profitEstimate,cmpBid,impressionTarget," +
				"uncorrectedProfitEstimate,costEstimate,estImpCost,estUcsCost,qualityChange,estQualityChange," +
				"ucsCost,estCostAcc,estProfitAcc,uncorrectedProffitAcc,estQualityChangeAcc,impTargetFulfillment," +
				"bidVs2ndRatio,profit,profitPerImpression,reachFulfillment,estUcsCostAcc";

		int impsTogo() {
			return (int) Math.max(0, reachImps - stats.getTargetedImps());
		}
		void setStats(CampaignStats s) {
			stats.setValues(s);
		}
		public AdxQuery[] getCampaignQueries() {
			return campaignQueries;
		}
		public void setCampaignQueries(AdxQuery[] campaignQueries) {
			this.campaignQueries = campaignQueries;
		}


		/**
		 * Calculates an estimate for impression targets (and profit) to maximise estimated profit.
		 * Considers the effect of short term cost of the campaign, long term effect of quality change
		 * and inaccuracies in previous predictions. By evaluating estimated profits for a variety of
		 * different impression targets.
		 */
		private void setImpressionTargets() {
			long target = 0;
			double estProfit = -99999, ERR, estQuality = 0, estCost = 0;
			// Consider a range of possible impression targets
			for (double multiplier = 0.6; multiplier <= 2; multiplier+= 0.02){ // loop over range of impression targets
				long tempTarget = (long)(this.reachImps*multiplier);

				// Estimate quality change
				double currentQuality = adNetworkDailyNotification.getQualityScore();
				double lRate = 0.6, Budget;
				ERR = ERRcalc(this, target);
				double tempEstQuality = (1 - lRate)*currentQuality + lRate*ERR;

				// If we haven't won the campaign yet estimate the budget.
				if (this.budget != 0){ Budget = this.budget; }
				else Budget = 0; //TODO ALUN: mean budget/impression from past * impressions;
				// Budget = sliding scale of historic average budget to game average budget
				// Go from 100% historical to 50% historical
				// Evaluate per impression then multiply by number of impressions
				// Loop over entries in the game and calculate an average
				// Loop over entries in the history and take an average


				// Estimate cost to run campaign at this level
				double tempEstCost = campaignCost(this, tempTarget, false);
				double tempEstProfit = Budget * ERR + qualityEffect(this, estQuality) - tempEstCost;

				// Decide which impression target is most cost efficient
				if (tempEstProfit > estProfit) {
					target = tempTarget;
					estProfit = tempEstProfit;
					estCost = tempEstCost;
					estQuality = tempEstQuality;
				}
			}

			// Save ucs cost and impression cost estimate to the campaign.
			campaignCost(this, target, true);

			// Factor in any bias we may have (adjust for difference in prediction and result)
			// TODO historic data
			double cumProfitEstimate = 0.0;
			double cumProfit = 0.0;
			// calculate total profit and estimated profit from ended campaigns.
			for (Map.Entry<Integer, CampaignData> entry : myCampaigns.entrySet()) {
				if (entry.getValue().dayEnd < day){
					CampaignData campaign = entry.getValue();
					cumProfit += campaign.profit;
					cumProfitEstimate += campaign.uncorrectedProfitEstimate;
				}
			}
			// error factor: ratio between average profit and average estimated profit
			double profitError = cumProfit / cumProfitEstimate;
			uncorrectedProfitEstimate = estProfit - qualityEffect(this,estQuality);
			profitEstimate = uncorrectedProfitEstimate * profitError;
			impressionTarget = target;
			costEstimate = estCost;

		}
	}

	/**
	 * Class for storing campaign data from previous games to be used in historic calculations
	 */
	private class historicCampaignData {

		public ArrayList<CampaignData> historicCampaigns;

		public historicCampaignData() {
			historicCampaigns = new ArrayList<>();
		}

		public int getNumberOfRecords() {
			return historicCampaigns.size();
		}

		public CampaignData getValue(int i) {
			return historicCampaigns.get(i);
		}

		// Calculates the Xth% quantile
		// Allows for flexible conservatism
		public double expectedLowBid(int confidence){
			DescriptiveStatistics statsCalc = new DescriptiveStatistics();

			for (CampaignData rEntry : historicCampaigns) {
				if (rEntry.profit < 0) {
					statsCalc.addValue(rEntry.cmpBid/(rEntry.stats.getTargetedImps()+rEntry.stats.getOtherImps()));
				}
			}
			double quantile = statsCalc.getPercentile(confidence);
			double mean = statsCalc.getMean();
			return quantile*pendingCampaign.reachImps;
		}

		// Sets the maximum bid price to be 1.1 * the average of top 10% of successful historic campaigns
		public double expectedHighBid(double confidence){
			DescriptiveStatistics statsCalc = new DescriptiveStatistics();

			for (CampaignData rEntry : historicCampaigns) {
				// for non randomly assigned campaigns
				if((rEntry.budget - rEntry.cmpBid) > 0.001){
					statsCalc.addValue(rEntry.cmpBid/rEntry.reachImps);
				}
			}
			double quantile = statsCalc.getPercentile(confidence);
			return quantile*pendingCampaign.reachImps;
		}


		/* DescriptiveStatistics statsCalc = new DescriptiveStatistics();
		double mean = 0;

		for(ImpressionRecord rEntry : impressionList) {
			if(rEntry.segGender == sGender) {
				statsCalc.addValue(rEntry.costImpr);
			}
		}
		mean = statsCalc.getMean();
		System.out.println("#####STATMEAN##### Historic mean per gender " + sGender + ":" + mean);
		return mean;
	} */
		/**
		 * Loads the historic campaign data from the csv file in filepath
		 */
		public void loadDataFromFile(String filepath) {
			try {
				Scanner scanner = new Scanner(new FileReader(filepath));
				String line;
				line = scanner.nextLine();
				CampaignData record;

				scanner.nextLine();
				while(scanner.hasNextLine()) {
					line = scanner.nextLine();
					String[] results = line.split(",");

					int game = Integer.parseInt(results[0]);
					int id = Integer.parseInt( results[1] );
					long dayStart = Long.parseLong(( results[2] ));
					long dayEnd = Long.parseLong(( results[3] ));
					Long reachImps = Long.parseLong(( results[4] ));
					String targetSegment = results[5]; //TODO ALUN: store target segment as correct type
					double videoCoef = Double.parseDouble((results[6]));
					double mobileCoef = Double.parseDouble((results[7]));
					double adxCost = Double.parseDouble((results[8]));
					double targetedImps = Double.parseDouble((results[9]));
					double untargetedImps = Double.parseDouble((results[10]));
					double budget = Double.parseDouble((results[11]));
					double revenue = Double.parseDouble((results[12]));
					double profitEstimate = Double.parseDouble((results[13]));
					double cmpBid = Double.parseDouble((results[14]));
					long impressionTarget = Long.parseLong(( results[15] ));
					double uncorrectedProfitEstimate = Double.parseDouble((results[16]));
					double costEstimate = Double.parseDouble((results[17]));
					double estImpCost = Double.parseDouble((results[18]));
					double estUcsCost = Double.parseDouble((results[19]));
					double qualityChange = Double.parseDouble((results[20]));
					double estQualityChange = Double.parseDouble((results[21]));
					double ucsCost = Double.parseDouble((results[22]));
					double estCostAcc = Double.parseDouble((results[23]));
					double estProfitAcc = Double.parseDouble((results[24]));
					double uncorrectedProfitAcc = Double.parseDouble((results[25]));
					double estQualityChangeAcc = Double.parseDouble((results[26]));
					double impTargetFulfillment = Double.parseDouble((results[27]));
					double bidVs2ndRatio = Double.parseDouble((results[28]));
					double profit = Double.parseDouble((results[29]));
					double profitPerImpression = Double.parseDouble((results[30]));
					double reachFulfillment = Double.parseDouble((results[31]));
					double estUcsCostAcc = Double.parseDouble((results[32]));

					//TODO ALUN: fix target segment and campaign queries
					record = new CampaignData(game,reachImps, dayStart, dayEnd, currCampaign.targetSegment, videoCoef, mobileCoef, id,
							currCampaign.campaignQueries, new CampaignStats(targetedImps,untargetedImps,adxCost),budget, revenue, profitEstimate, cmpBid,
							impressionTarget, uncorrectedProfitEstimate, costEstimate, estImpCost, estUcsCost,
							qualityChange, estQualityChange, ucsCost, estCostAcc, estProfitAcc, uncorrectedProfitAcc,
							estQualityChangeAcc, impTargetFulfillment, bidVs2ndRatio, profit, profitPerImpression,
							reachFulfillment, estUcsCostAcc);

					historicCampaigns.add(record);
				}
				scanner.close();
			} catch (Exception e) {
				System.out.println("Error: " + e.getMessage());
			}
		}

	}


	/**
	 *  User defined methods
	 */

	/*
	 * ALUN: different methods for each campaign strategy
     */

	public double expectedLowBid(double confidence){
		DescriptiveStatistics statsCalc = new DescriptiveStatistics();
		int numUnprofitable = 0;

		// only list unprofitable campaigns
		for (Map.Entry<Integer, CampaignData> entry : myCampaigns.entrySet()) {
			CampaignData campaign = entry.getValue();
			if (campaign.profit < 0) {
				numUnprofitable++;
				statsCalc.addValue(campaign.cmpBid/campaign.reachImps);
			}
		}
		double lowBid = statsCalc.getPercentile(confidence);
		// Only returns a value if there are enough datapoints
		if (numUnprofitable <= 3) {lowBid = 0;}
		return lowBid*pendingCampaign.reachImps;
	}

	// Returns maximum value in the current game
	public double expectedHighBid(){
		DescriptiveStatistics statsCalc = new DescriptiveStatistics();

		// only list non-randomly given campaigns
		for (Map.Entry<Integer, CampaignData> entry : myCampaigns.entrySet()) {
			CampaignData campaign = entry.getValue();
			if (campaign.profit < 0) {
				statsCalc.addValue(campaign.cmpBid/campaign.reachImps);
			}
		}
		double highBid = statsCalc.getMax();
		return highBid*pendingCampaign.reachImps;
	}

	// Method for calculating an ERR value for a specific target.
	private double ERRcalc(CampaignData campaign, long target) {
		double a = 4.08577, b = 3.08577, fracComplete = (double)target/(double)campaign.reachImps;
		double ERR = (2/a) * (( Math.atan((a*fracComplete) - b )) - Math.atan(-b));
		return ERR;
	}
	/**
	 Goes through historical (previously trained) data and evaluates when the bid is too low to be profitable
	 at a confidence level given.
	 Does not take into account environmental factors (is not a prediction of profitability) just a lower bound.
	 Therefore keep the confidence high
	 NOTE: this function should be turned off while training historical data
	 */
	private double bidTooLow(long cmpimps, int confidence) {
		// Scales between historic and current values
		// gets median value of unprofitable campaigns (removes 2 s.d. from that and sets as minimum bid)
		// Median so outliers don't push it too far, 2 s.d. to be conservative because no other variables being considered
		double bidLowCurrent = expectedLowBid(confidence);
		double bidLowHistoric = historicCampaigns.expectedLowBid(confidence);
		int length = startInfo.getNumberOfDays();
		if(bidLowCurrent == 0) bidLowCurrent = bidLowHistoric;
		double bidLow = (HISTORIC_FRAC * bidLowHistoric * (length - day)/length) +
				((1-HISTORIC_FRAC) * bidLowCurrent * day/length);
		double reserve = cmpimps * 0.0001 / adNetworkDailyNotification.getQualityScore();
		if (bidLow < reserve) {bidLow = reserve;}
		System.out.println(" Min: " + (long)(bidLow*1000));
		return bidLow;
	}

	/**
	 * Goes through all previously successful bids and evaulates the maximum value to which
	 * a successful bid can be place, either the 90% quantile of previous games, max successful
	 *campaign in this game (or reserve price).
	 */
	private double bidTooHigh(long cmpimps, int percentFailure) {
		double bidHighCurrent;
		double bidHighHistoric;
		if (historicCampaigns.expectedHighBid(percentFailure) > 0){
			bidHighHistoric = historicCampaigns.expectedHighBid(percentFailure);
		} else bidHighHistoric = 0;
		if (expectedHighBid() > 0) {bidHighCurrent = expectedHighBid();}
		else bidHighCurrent = 0;
		double reserve = (0.001*cmpimps*percentFailure)/100;
		double bidHigh = Math.min(1.1*Math.max(bidHighHistoric, bidHighCurrent), reserve);
		System.out.println("bidHighHistoric, " + bidHighHistoric + " bidHighCurrent " + bidHighCurrent + " reserve, " + reserve);
		// Make sure bid is still below maximum price.
		double bidMax = 0.001 * cmpimps * adNetworkDailyNotification.getQualityScore();
		if(bidHigh >= reserve) {bidHigh = bidMax;}
		System.out.print(" MaxBid: " + (long)(1000*bidMax) + " MinMax: " + (long)(1000*reserve) + " Bid " + bidHigh + "@@@");
		return bidHigh;
	}

	/**
	 * Method for computing campaign bid to maximise profit
	 * Currently just bids randomly between min and max as before
	 * In progress: version will bid the average successful second price.
	 * Not great because it creates a system where we assign our value based on other agents value.
	 */
	private double campaignProfitStrategy() {
		Random random = new Random();
		double bid, bidFactor;
		double totalCostPerImp = 0.0;
		if (myCampaigns.size() >= 3) {
			for (Map.Entry<Integer, CampaignData> entry : myCampaigns.entrySet()) {
				if (entry.getValue().dayStart != 1) {
					totalCostPerImp += entry.getValue().budget / entry.getValue().reachImps;
				}
			}
			bidFactor = (random.nextInt(40)/100) + 0.8;
			bid = pendingCampaign.reachImps * totalCostPerImp / (myCampaigns.size() -1) * bidFactor;
		}
		//else bid = (double)random.nextInt(pendingCampaign.reachImps.intValue())/1000; //Random bid initially
		else {
			cmpBidPerImp *= 0.9;
			bid = cmpBidPerImp * pendingCampaign.reachImps;
		}
		System.out.println("Day " + day + ": Campaign - Base bid(millis): " + (long)(1000*bid));
		return bid;

		/* Main strategy
		 * estimates the cost of pendingCampaign - campaignCost(pendingCampaign);
		 * Add some level of minimum profit (risk)
		 * Bid this value... easy peasy.
		 * pendingCampaign.setImpressiontarget();
		 * return (pendingCampaign.estProfit + pendingCampaign.estCost ) * 1.1;
         */
	}

	/*
	 * Method for computing the quality recovery campaign bid strategy
	 * Multiply profit strategy by quality squared, first to turn our bid into an effective bid.
	 * Second to try and win more campaigns than our value assigns.
	 * Quality associated with revenue is accounted for already, this effect is to account for
	 * the reduced number of won campaigns.
	 */
	private double campaignQualityRecoveryStrategy() {
		double bid =  campaignProfitStrategy() * Math.pow(adNetworkDailyNotification.getQualityScore(),2);
		System.out.println("Day " + day + ": Campaign - Quality Recovery Strategy");
		/*
		TODO ALUN: Historic Data
		TODO: ferocity of quality recovery should be based on our ability to complete the campaigns and the number of campaigns we currently have.
		- if impression targets for current campaigns is above average impressions per day then have a negative
		quality recovery effect.
		*/
		// Retrieve average impressions per day
		// This isn't great because it doesn't represent the number of impressions we COULD get per day.
		// Retrieve sum of impression targets for current campaiagns.
		// Augment bid by profit strategy * quality rating * fraction of
		return bid;
	}

	/*
	 * Method for computing the campaign bid for starting strategy
	 * TODO ALUN: Evaluate how to base these weights.
	 */
	private double campaignStartingStrategy() {
		double cmpBid;
		long campaignLength = pendingCampaign.dayEnd - pendingCampaign.dayStart + 1;
		if(campaignLength == 10){ // long campaign
			cmpBid = campaignProfitStrategy()*0.8;
			System.out.println("Day " + day +  ": Campaign - Long campaign Starting Strategy");
		}
		else if (campaignLength == 5){ // medium campaign
			cmpBid = campaignProfitStrategy()*1.5;
			System.out.println("Day: " + day + " Campaign - Medium campaign Starting Strategy");
		}
		else { // short campaign
			cmpBid = campaignProfitStrategy()*2;
			System.out.println("Day " + day + ": Short campaign Starting Strategy");
		}
		return cmpBid;
	}

	/*
	 * Evaluates the effect of estimated quality change on future revenue.
	 */
	private double qualityEffect(CampaignData Campaign, double estQuality) {
		// Days remaining after campaign ends
		long daysRemaining = 60 - Campaign.dayEnd;
		double pastIncome = 0.0;
		for (Map.Entry<Integer, CampaignData> entry : myCampaigns.entrySet()) {
			CampaignData campaign = entry.getValue();
			if (campaign.dayEnd < day)
				pastIncome += campaign.revenue;
			else { // Smooths out estimate by adding fractional completeness of ongoing campaigns (approximated by budget)
				pastIncome += campaign.budget * (1 - campaign.impsTogo()/campaign.reachImps);
			}
		}
		double historicDailyIncome = 1;// TODO ALUN: machine learning
		double pastDailyIncome = pastIncome / day;
		// Linearly reduces reliance on historic data --> dynamic data over time
		long revenueRemaining = (long)((daysRemaining/60)*daysRemaining*historicDailyIncome
				+ pastDailyIncome*(1-daysRemaining/60)*daysRemaining);
		double qualityChange = estQuality - adNetworkDailyNotification.getQualityScore();
		// todo use a more accurate model than linear quality change * revenueRemaining
		return qualityChange * revenueRemaining;
	}

	/*
	 * Estimates the total cost of running a campaign based on the sum UCS and impression estimation functions.
	 * Total cost = impression cost of campaign + ucs cost
	 */
	private double campaignCost(CampaignData Campaign, long targetImp, boolean save) {
		double totalCost = 0;
		double impressionCost = 0;
		double ucsCost = 0;
		// todo campaign cost = cost paid of past impressions paid for + cost of future impressions
		// todo try not to include ucs cost twice for overlapping campaigns.
		// loop over each day of the campaign
		if( Campaign.dayEnd < day) {totalCost = Campaign.stats.getCost();} //todo include historic ucs cost here
		else
			for (int Day = (int)Campaign.dayStart; Day <= (int)Campaign.dayEnd; Day++) {
				if (Day < day){
					totalCost += Campaign.stats.getCost();
					Day = day -1; // Because reports cumulative stats.
				}
				// evaluate best UCS/impression cost combination estimation
				ucsTargetLevel = bestImpUcsCombination(targetImp);
				// add the UCS cost to the Impression cost estimate and sum
				// TODO add desperation coefficient to bid
				impressionCost += impressionCostEstimate(targetImp/(Campaign.dayEnd-Campaign.dayStart), Day, ucsTargetLevel);
				ucsCost += ucsCostEstimate(ucsTargetLevel); //todo divide ucs cost by #overlapping campaigns
			}
		if ((save == true)& (day == Campaign.dayStart - 1)) {Campaign.estImpCost = impressionCost; Campaign.estUcsCost = ucsCost;};
		totalCost += impressionCost + ucsCost;
		return totalCost;
	}

	/**
	 // Write a class of performance metrics which update daily throughout the game (and print out)
	 // Print these performance metrics to a file so they can be manually inspected.
	 // estimated cost accuracy, estimated profit accuracy, impression target fulfillment, price bid vs second price.
	 // Profit, profit per impression.
	 */
	private class PerformanceData {
		// averages over campaigns
		double avBidVs2ndRatio;
		double avReachImps;
		double profitPerImpression;
		double reachFulfillment;
		long daystart; // Average day we bought campaigns in the game.
		double perDayUcsCost;
		double perImpImpCost;
		double perImpTargetedImpCost;
		double perImpUntargetedImpCos;
		double perDayImp;
		double perDayTargetedImp;
		double perDayUntargetedImp;
		double cmpProportionShort;
		double cmpProportionMedium;
		double cmpProportionLong;
		double cmpProportionLowReach;
		double cmpProportionMedReach;
		double getCmpProportionHighReach;
		double cmpBudget;
		double cmpBid;
		double cmpRevenue;
		double targetPerReach; // impression target set per impression in the campaign

		// Estimates
		double profitEstimate;
		double getUncorrectedProfitEstimate;

		// Prediction ability
		double impTargetFulfillment;
		double estCostAcc;
		double estUcsCostAcc;
		double estImpCostAcc;
		double estProfitAcc;
		double uncorrectedProfitEstimateAcc;

		// Running totals
		double revenue;
		double profit;
		int numCamps;
		double ucsCost;
		double impCost;

		/* Possible additions
		 * Overlapping segments (average number of competitors & average % of user population bid for)
		 *
		 */

		public PerformanceData() {
			estCostAcc = 0.0;
			estProfitAcc = 0.0;
			impTargetFulfillment = 0.0;
			reachFulfillment = 0.0;
			avBidVs2ndRatio= 0.0;
			profit = 0.0;
			profitPerImpression = 0.0;
			revenue= 0.0;
			numCamps = 0;
			uncorrectedProfitEstimateAcc=0.0;
			estUcsCostAcc =0.0;
			estImpCostAcc =0.0;

		}
		public void setReachFulfillment(CampaignData a) {
			reachFulfillment = (reachFulfillment * (numCamps - 1) + a.reachFulfillment) / numCamps;
		}
		public void setEstUcsCostAcc(CampaignData a) {
			estUcsCostAcc = (estUcsCostAcc * (numCamps - 1) + a.estUcsCost) / numCamps;
		}
		public void setEstImpCostAcc(CampaignData a) {
			estImpCostAcc = (estImpCostAcc * (numCamps - 1) + a.estImpCost) /numCamps;
		}
		public void setEstCostAcc(CampaignData a) {
			estCostAcc = (estCostAcc * (numCamps - 1) + a.estCostAcc) / numCamps;
		}
		public void setEstProfitAcc(CampaignData b){
			estProfitAcc = (estProfitAcc * (numCamps - 1) + b.estProfitAcc) / numCamps;
		}
		public void setUncorrectedProfitEstimate(CampaignData b){
			uncorrectedProfitEstimateAcc = (uncorrectedProfitEstimateAcc * (numCamps - 1) + b.uncorrectedProfitEstimate) / numCamps;
		}
		public void setImpTargetFulfillment(CampaignData c){
			impTargetFulfillment = (impTargetFulfillment * (numCamps - 1) + c.impTargetFulfillment) / numCamps;
		}
		public void setBidVs2ndRatio(CampaignData d) {
			if (d.budget != d.cmpBid){
				avBidVs2ndRatio = (avBidVs2ndRatio * (numCamps - 1) + d.bidVs2ndRatio) / numCamps;
			}
		}
		public void setProfit(CampaignData e){
			profit +=  e.profit;
		}
		public void setRevenue(CampaignData e){
			revenue += e.revenue;
		}
		public void setProfitPerImpression(CampaignData f){
			profitPerImpression = (profitPerImpression * (numCamps - 1) + f.profitPerImpression) / numCamps;
		}
		public void incrementNumCamps(){ numCamps = numCamps + 1;}

		public void updateData(CampaignData x){
			incrementNumCamps();
			setEstCostAcc(x);
			setEstProfitAcc(x);
			setImpTargetFulfillment(x);
			setBidVs2ndRatio(x);
			setProfit(x);
			setRevenue(x);
			setProfitPerImpression(x);
			setUncorrectedProfitEstimate(x);
			setEstImpCostAcc(x);
			setEstUcsCostAcc(x);
			setReachFulfillment(x);
			setEstUcsCostAcc(x);
		}
	}
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 *  Impression cost estimate
	 *  This method takes an impression target as an input and evaluates the estimated cost to achieve that value given
	 *  the day and UCS target.
	 *  Again later it will be useful to be able to estimate costs for a range of impressions to choose a local optimum.
	 *  May include functionality to recall past actual costs / estimates.
	 *  USE: use this function for campaign bids by evaluating the best impression/UCS combination then summing over
	 *  all days of the prospective campaign to evaluate total cost to complete campaign.
	 */
	private double impressionCostEstimate(long impTarget, long day, int ucsTargetLevel) {
		return 0.0006*impTarget;
	}
	private double ImpressionCostEstimator() {
		double EstimateCostOfImpressionsToday = 0;
		CampaignData itemFor1, itemFor2;
		// TODO;
		try {
			/*for (CampaignData itemFor2 : campaignsInGame) {
				System.out.println("##################################3   " + day);

				System.out.println(itemFor2);
			}*/
			/*for (Map.Entry<Integer, CampaignData> campaignInGame : campaignsInGame.entrySet()) {
				System.out.println("algo???");
				System.out.println(campaignInGame.getValue().dayStart);
			}*/

			for (Map.Entry<Integer, CampaignData> campaign : myCampaigns.entrySet()) {

				itemFor1 = campaign.getValue();
				if (itemFor1.dayStart <= day) {
					if (itemFor1.dayEnd >= day) {
						itemFor1.popInSegmentOfOurCampaign = 1;
						itemFor1.popInSegmentOfOurCampaign = itemFor1.reachImps / ((double) (itemFor1.dayEnd - itemFor1.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
						//System.out.println("Popularity of our campaign: " + itemFor1.popInSegmentOfOurCampaign);
						/*itemFor1.popInSegmentOfOurCampaign = itemFor1.reachImps / ((itemFor1.dayEnd - itemFor1.dayStart));
						System.out.println("Popularity of our campaign (1): "  + itemFor1.popInSegmentOfOurCampaign);
						itemFor1.popInSegmentOfOurCampaign = itemFor1.reachImps / (MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
						System.out.println("Popularity of our campaign (2): "  + itemFor1.popInSegmentOfOurCampaign);
						itemFor1.popInSegmentOfOurCampaign = 1 / ((double)(itemFor1.dayEnd - itemFor1.dayStart) * (double)MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
						System.out.println("Popularity of our campaign (3): "  + itemFor1.popInSegmentOfOurCampaign);
					*/
						for (Map.Entry<Integer, CampaignData> campInGame : campaignsInGame.entrySet()) {

							itemFor2 = campInGame.getValue();
							/*System.out.println("Campaigns thrown by the game  " + itemFor2.id);*/
							if (itemFor2.dayStart <= day) {
								if (itemFor2.dayEnd >= day) {
									if (itemFor2.id != itemFor1.id) {
										if (itemFor1.targetSegment.contains(MarketSegment.MALE)) {
											if (!itemFor2.targetSegment.contains(MarketSegment.FEMALE)) {
												//System.out.println("Competing campaign: " + itemFor2.id + "not female");
												if (itemFor1.targetSegment.contains(MarketSegment.OLD)) {
													if (!itemFor2.targetSegment.contains(MarketSegment.YOUNG)) {

														if (itemFor1.targetSegment.contains(MarketSegment.HIGH_INCOME)) {
															if (!itemFor2.targetSegment.contains(MarketSegment.LOW_INCOME)) {
																itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
																/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA");
																System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
															*/
															}
														} else if (itemFor1.targetSegment.contains(MarketSegment.LOW_INCOME)) {
															if (!itemFor2.targetSegment.contains(MarketSegment.HIGH_INCOME)) {
																itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
																/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA");
																System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
															*/
															}
														} else {
															itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
															/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA");
															System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
														*/
														}

													}
												} else if (itemFor1.targetSegment.contains(MarketSegment.YOUNG)) {
													if (!itemFor2.targetSegment.contains(MarketSegment.OLD)) {

														if (itemFor1.targetSegment.contains(MarketSegment.HIGH_INCOME)) {
															if (!itemFor2.targetSegment.contains(MarketSegment.LOW_INCOME)) {
																itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
																/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA");
																System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
															*/
															}
														} else if (itemFor1.targetSegment.contains(MarketSegment.LOW_INCOME)) {
															if (!itemFor2.targetSegment.contains(MarketSegment.HIGH_INCOME)) {
																itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
																/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA");
																System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
															*/
															}
														} else {
															itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
															/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA");
															System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
														*/
														}

													}
												} else {
													if (itemFor1.targetSegment.contains(MarketSegment.HIGH_INCOME)) {
														if (!itemFor2.targetSegment.contains(MarketSegment.LOW_INCOME)) {
															itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
															/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA");
															System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
														*/
														}
													} else if (itemFor1.targetSegment.contains(MarketSegment.LOW_INCOME)) {
														if (!itemFor2.targetSegment.contains(MarketSegment.HIGH_INCOME)) {
															itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
															/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA");
															System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
														*/
														}
													} else {

														itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
														/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA");
														System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
													*/
													}
												}
											}
										} else if (itemFor1.targetSegment.contains(MarketSegment.FEMALE)) {
											if (!itemFor2.targetSegment.contains(MarketSegment.MALE)) {

												if (itemFor1.targetSegment.contains(MarketSegment.OLD)) {
													if (!itemFor2.targetSegment.contains(MarketSegment.YOUNG)) {

														if (itemFor1.targetSegment.contains(MarketSegment.HIGH_INCOME)) {
															if (!itemFor2.targetSegment.contains(MarketSegment.LOW_INCOME)) {
																itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
																/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA");
																System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
															*/
															}
														} else if (itemFor1.targetSegment.contains(MarketSegment.LOW_INCOME)) {
															if (!itemFor2.targetSegment.contains(MarketSegment.HIGH_INCOME)) {
																itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
																/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA");
																System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
															*/
															}
														} else {
															itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
															/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA               F,O nuestra,    notM, notY");
															System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
														*/
														}
													}
												} else if (itemFor1.targetSegment.contains(MarketSegment.YOUNG)) {
													if (!itemFor2.targetSegment.contains(MarketSegment.OLD)) {

														if (itemFor1.targetSegment.contains(MarketSegment.HIGH_INCOME)) {
															if (!itemFor2.targetSegment.contains(MarketSegment.LOW_INCOME)) {
																itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
																/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA");
																System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
															*/
															}
														} else if (itemFor1.targetSegment.contains(MarketSegment.LOW_INCOME)) {
															if (!itemFor2.targetSegment.contains(MarketSegment.HIGH_INCOME)) {
																itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
																/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA");
																System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
															*/
															}
														} else {
															itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
															/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA");
															System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
														*/
														}
													}
												} else {
													if (itemFor1.targetSegment.contains(MarketSegment.HIGH_INCOME)) {
														if (!itemFor2.targetSegment.contains(MarketSegment.LOW_INCOME)) {
															itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
															/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA");
															System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
														*/
														}
													} else if (itemFor1.targetSegment.contains(MarketSegment.LOW_INCOME)) {
														if (!itemFor2.targetSegment.contains(MarketSegment.HIGH_INCOME)) {
															itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
															/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA");
															System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
														*/
														}
													} else {

														itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
														/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA");
														System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
													*/
													}
												}
											}
										} else {
											if (itemFor1.targetSegment.contains(MarketSegment.OLD)) {
												if (!itemFor2.targetSegment.contains(MarketSegment.YOUNG)) {

													if (itemFor1.targetSegment.contains(MarketSegment.HIGH_INCOME)) {
														if (!itemFor2.targetSegment.contains(MarketSegment.LOW_INCOME)) {
															itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
															/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA    O,H nuestra,  notY,notL");
															System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
														*/
														}
													} else if (itemFor1.targetSegment.contains(MarketSegment.LOW_INCOME)) {
														if (!itemFor2.targetSegment.contains(MarketSegment.HIGH_INCOME)) {
															itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
															/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA     O,L nuestra,   notY, not H");
															System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
														*/
														}
													} else {

														itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
														/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA          O nuestra,     notY");
														System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
													*/
													}
												}
											} else if (itemFor1.targetSegment.contains(MarketSegment.YOUNG)) {
												if (!itemFor2.targetSegment.contains(MarketSegment.OLD)) {

													if (itemFor1.targetSegment.contains(MarketSegment.HIGH_INCOME)) {
														if (!itemFor2.targetSegment.contains(MarketSegment.LOW_INCOME)) {
															itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
															/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA");
															System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
														*/
														}
													} else if (itemFor1.targetSegment.contains(MarketSegment.LOW_INCOME)) {
														if (!itemFor2.targetSegment.contains(MarketSegment.HIGH_INCOME)) {
															itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
															/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA");
															System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
														*/
														}
													} else {
														itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));

														/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA");
														System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
													*/
													}
												}
											} else {
												if (itemFor1.targetSegment.contains(MarketSegment.HIGH_INCOME)) {
													if (!itemFor2.targetSegment.contains(MarketSegment.LOW_INCOME)) {

														itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
														/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA");
														System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
													*/
													}
												} else if (itemFor1.targetSegment.contains(MarketSegment.LOW_INCOME)) {
													if (!itemFor2.targetSegment.contains(MarketSegment.HIGH_INCOME)) {

														itemFor1.popInSegmentOfOurCampaign = itemFor1.popInSegmentOfOurCampaign + itemFor2.reachImps / ((double) (itemFor2.dayEnd - itemFor2.dayStart) * (double) MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment));
														/*System.out.println("PRUEBA PRUEBA PRUEBA PRUEBA PRUEAB PRUEBA");
														System.out.println("Campaign competing: " + campInGame + "size of MARKET SEGMENT" + MarketSegment.usersInMarketSegments().get(itemFor2.targetSegment) + "; with OUR campaign: " + campaign + "size of OUR marketSegment" + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
													*/
													}
												}
											}
										}
									}
								}
							}
						}

						if (itemFor1.dayStart == day) {
							///// Initial Reserve Price (between 0 and 0.005) -> Set as Maximum
							itemFor1.ReservePriceEstimated = 0.005;
						} else {
							//   Trying with average value of impressions in a game:  0.0012
							itemFor1.ReservePriceEstimated = (0.2 * (double) itemFor1.ReservePriceEstimated + 0.8 * (0.0012) * (double) itemFor1.popInSegmentOfOurCampaign);
						}
						//int randomNumber = random.nextInt(2) - 1;  add random number between 0.04 and -0.04 -> Set as Maximum
						itemFor1.ReservePriceThisDay = itemFor1.ReservePriceEstimated + 0.04;

						// Cost Estimate by a factor of 0.0012
						itemFor1.impCostEstThisDay = itemFor1.ReservePriceEstimated + (0.1) * itemFor1.popInSegmentOfOurCampaign / adNetworkDailyNotification.getServiceLevel();
						//itemFor1.impressionCostEstimate = itemFor1.impCostEstThisDay*(60-day)/60 + itemFor1.impCostAvg*(day)/60;

						EstimateCostOfImpressionsToday = EstimateCostOfImpressionsToday + itemFor1.impCostEstThisDay;
						// Correct with days: at the end there is less competence *60/(60+day)

						//System.out.println("####################################################################");
						System.out.println("Active Campaigns NAMM: " + campaign.getValue().id +";  Estimation: " +itemFor1.impCostEstThisDay);
						//System.out.println("####################################################################");


						/*System.out.println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&");
						System.out.println("CAMPAIGN" + itemFor1.id + "-->" + "reachImps = " + itemFor1.reachImps + ";  dayStart = " + itemFor1.dayStart + ";  dayEnd = " + itemFor1.dayEnd + ";  TargetSegmentSize = " + MarketSegment.usersInMarketSegments().get(itemFor1.targetSegment));
						System.out.println("----CAMPAIGN" + itemFor1.id + "-->  Popularity:" + itemFor1.popInSegmentOfOurCampaign + ". IMPRESSION COST ESTIMATE TODAY:" +itemFor1.impCostEstThisDay + "------");
						System.out.println("&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&");
					*/



					}
				}

				/*System.out.println("################################################  Prueba 2");
				System.out.println("Campañas activas de NAMM: " + campaign.getValue().id);
				*/
			}
		}
		catch(Exception ex){
			System.out.println(ex.getMessage());
			ex.printStackTrace();
		}
		System.out.println("Estimate Cost of all impressions: " + EstimateCostOfImpressionsToday);
		return EstimateCostOfImpressionsToday;
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * Nicola: UCS cost estimate
	 * This function estimates the cost to achieve a specific ucs tier. Note that ucsTarget is the integer tier not the
	 * percentage of users classified. (1 = 100%, 2 = 90%, 3 = 81% ...).
	 * Expansion: Factor in changes to unknown and known impression costs.
	 */
	private double ucsCostEstimate(int ucsTargetLevel) {
		// TODO;
		return 0.15;  // default bidding is random (and so are dummy agents)
	}

	/**
	 * Nicola: Best UCS impression cost combination
	 * This function queries impression and UCS cost estimations over the entire range of UCS costs to evaluate the
	 * cheapest cost to achieve our impression target.
	 */
	private int bestImpUcsCombination(long targetImpressions){
		// TODO Return desired UCS classification;
		return 1; // default desire to get first place (100%)
	}

	/**
	 * Nicola: UCS bid
	 * This method takes a UCS target and tries to evaluate the bid required to reach that target.
	 * Expansion: include a sense of risk aversion to this function. i.e. when it is more important to achieve a
	 * specific UCS level (like incomplete campaign due that day) we want to overbid.
	 * Will use a combination of machine learning techniques and recalling recorded values to produce estimate.
	 */
	private double ucsBidCalculator(int ucsTargetLevel){
		// TODO;
		return ucsBid();
	}

	//TODO ALUN ALUN
	private double ucsBid(){
		double initbid = 0.25;
		double scale = 0.17;
		double ucsbid = 0;
		int numRunningCampaings = 0;
		for (Map.Entry<Integer, CampaignData> entry : myCampaigns.entrySet()) {
			if ((entry.getValue().dayEnd >= day) & (entry.getValue().dayStart <= day))	{
				numRunningCampaings ++;
			}
		}
		if (numRunningCampaings != 0)
		{
			if (day <= 10)
			{ucsbid = initbid;}
			else
			{
				ucsbid = scale*Math.cbrt(numRunningCampaings);
			}  // avg n of campaigns 60/8=7.5   cbrt(7.5)=1.957434
			ucsbid = ucsbid + ucsPerceptron;
		}

		return ucsbid;    // avg bid = 0.8556
	}

	/**
	 * Miguel: This method calculates how to bid for unknown users.
	 */
	private double untargetedImpressionBidCalculator(double impressionTarget){
		// TODO;
		return 0;
	}

	/**
	 * Miguel: This method evaluates the bid for each impression query.
	 * This method uses getStatsPerAllCriteria that filters records from the impression bid history by selecting only those
	 * proved bids that are successful (all or most impressions won) to define the new base price (return line).
	 */
	private double ImpressionBidCalculator(int impressionTarget, AdxQuery iQuery){
		BasicStatisticValues histImprStats;
		histImprStats = impressionBidHistory.getStatsPerAllCriteria(iQuery);
		return (histImprStats.mean + (histImprStats.std * 2)) * 1000 * impressionTarget * 1000;
	}

	/**
	 * Class to keep a record of all historic bid results coming from the server. This is useful for
	 * future estimates and support in general the campaigns and impressions bidding strategy.
	 */
	private class ImpressionHistory {
		// Main collection. Record list of type ImpressionRecord defined below
		public List<ImpressionRecord> impressionList;

		/**
		 * Constructor method. Basically initializes the ArrayList at the beginning of the game when
		 * an instance of AgentNAMM is created
		 */
		public ImpressionHistory(){
			impressionList = new ArrayList<ImpressionRecord>();
		}

		/**
		* Method to filter impression bid entries that are proven to be successful (bids = wons, or as implemented, 
		* rEntry.lostCount == 0). This filter is applied for each specific target profile by checking the combination of 
		* age, income and gender. The if sequence goes from the most specific cases to the most general ones. Finally,
		* a statistical summary is created using a DescriptiveStatistics object from the org.apache.commons.math3 package.
		*
		* This method became obsolete during the development and was replaced by getStatsPerAllCriteria
		**/
		public BasicStatisticValues getStatsPerSegment(MarketSegment sGender, MarketSegment sAge, MarketSegment sIncome){
			DescriptiveStatistics statsCalc = new DescriptiveStatistics();
			BasicStatisticValues returnVal = new BasicStatisticValues();

			if(sGender != null && sAge != null && sIncome != null){
				for(ImpressionRecord rEntry : impressionList) {
					if(rEntry.mktSegGender == sGender && rEntry.mktSegAge == sAge && rEntry.mktSegIncome == sIncome && rEntry.lostCount == 0) {
						statsCalc.addValue(rEntry.costImpr);
					}
				}
			}
			else if (sGender != null && sAge != null) {
				for(ImpressionRecord rEntry : impressionList) {
					if(rEntry.mktSegGender == sGender && rEntry.mktSegAge == sAge && rEntry.lostCount == 0) {
						statsCalc.addValue(rEntry.costImpr);
					}
				}
			}
			else if (sAge != null && sIncome != null) {
				for(ImpressionRecord rEntry : impressionList) {
					if(rEntry.mktSegAge == sAge && rEntry.mktSegIncome == sIncome && rEntry.lostCount == 0) {
						statsCalc.addValue(rEntry.costImpr);
					}
				}
			}
			else if (sGender != null && sIncome != null) {
				for(ImpressionRecord rEntry : impressionList) {
					if(rEntry.mktSegGender == sGender && rEntry.mktSegIncome == sIncome && rEntry.lostCount == 0) {
						statsCalc.addValue(rEntry.costImpr);
					}
				}
			}
			else if (sGender != null) {
				for(ImpressionRecord rEntry : impressionList) {
					if(rEntry.mktSegGender == sGender && rEntry.lostCount == 0) {
						statsCalc.addValue(rEntry.costImpr);
					}
				}
			}
			else if (sAge != null) {
				for(ImpressionRecord rEntry : impressionList) {
					if(rEntry.mktSegAge == sAge && rEntry.lostCount == 0) {
						statsCalc.addValue(rEntry.costImpr);
					}
				}
			}
			else if (sIncome != null) {
				for(ImpressionRecord rEntry : impressionList) {
					if(rEntry.mktSegIncome == sIncome && rEntry.lostCount == 0) {
						statsCalc.addValue(rEntry.costImpr);
					}
				}
			}

			if(statsCalc.getN() > 0){
				returnVal.mean = statsCalc.getMean();
				returnVal.std = statsCalc.getStandardDeviation();
				returnVal.var = statsCalc.getVariance();
				returnVal.max = statsCalc.getMax();
				returnVal.min = statsCalc.getMin();
			}

			return returnVal;
		}

		/**
		* Method to filter impression bid entries that are proven to be successful (bids = wons, or as implemented, 
		* rEntry.lostCount == 0). This filter is applied for each specific target profile by checking the combination of 
		* age, income and gender. The if sequence goes from the most specific cases to the most general ones. Finally,
		* a statistical summary is created using a DescriptiveStatistics object from the org.apache.commons.math3 package.
		*
		* This method was changed few times during the competition since filters were not working as expected, apparently due to
		* a file encoding issue.
		**/
		public BasicStatisticValues getStatsPerAllCriteria(AdxQuery pQuery){
			DescriptiveStatistics statsCalc = new DescriptiveStatistics();
			BasicStatisticValues returnVal = new BasicStatisticValues();
			MarketSegment sGender, sAge, sIncome;

			if(pQuery.getMarketSegments().contains(MarketSegment.MALE)) {
				sGender = MarketSegment.MALE;
			}
			else if(pQuery.getMarketSegments().contains(MarketSegment.FEMALE)) {
				sGender = MarketSegment.FEMALE;
			}
			else {
				sGender = null;
			}

			if(pQuery.getMarketSegments().contains(MarketSegment.YOUNG)) {
				sAge = MarketSegment.YOUNG;
			}
			else if(pQuery.getMarketSegments().contains(MarketSegment.OLD)) {
				sAge = MarketSegment.OLD;
			}
			else {
				sAge = null;
			}

			if(pQuery.getMarketSegments().contains(MarketSegment.HIGH_INCOME)) {
				sIncome = MarketSegment.HIGH_INCOME;
			}
			else if(pQuery.getMarketSegments().contains(MarketSegment.LOW_INCOME)) {
				sIncome = MarketSegment.LOW_INCOME;
			}
			else {
				sIncome = null;
			}

			// System.out.println("#####STATSALLCRITERIA##### " + pQuery.toString());

			if(sGender != null && sAge != null && sIncome != null){
				// System.out.print("#####STATSALLCRITERIA##### Gender + Age + Income");
				for(ImpressionRecord rEntry : impressionList) {
					if(rEntry.mktSegGender == sGender && rEntry.mktSegAge == sAge && rEntry.mktSegIncome == sIncome && rEntry.adType == pQuery.getAdType() && rEntry.dev == pQuery.getDevice()) {
						//  && rEntry.pub == pQuery.getPublisher() && rEntry.lostCount == 0
						statsCalc.addValue(rEntry.costImpr);
						//System.out.print(".");
					}
				}
			}
			else if (sGender != null && sAge != null) {
				System.out.print("#####STATSALLCRITERIA##### Gender + Age");
				for(ImpressionRecord rEntry : impressionList) {
					if(rEntry.mktSegGender == sGender && rEntry.mktSegAge == sAge && rEntry.adType == pQuery.getAdType() && rEntry.dev == pQuery.getDevice()) {
						statsCalc.addValue(rEntry.costImpr);
						System.out.print(".");
					}
				}
			}
			else if (sAge != null && sIncome != null) {
				System.out.print("#####STATSALLCRITERIA##### Age + Income");
				for(ImpressionRecord rEntry : impressionList) {
					if(rEntry.mktSegAge == sAge && rEntry.mktSegIncome == sIncome && rEntry.adType == pQuery.getAdType() && rEntry.dev == pQuery.getDevice()) {
						statsCalc.addValue(rEntry.costImpr);
						System.out.print(".");
					}
				}
			}
			else if (sGender != null && sIncome != null) {
				System.out.print("#####STATSALLCRITERIA##### Gender + Income");
				for(ImpressionRecord rEntry : impressionList) {
					if(rEntry.mktSegGender == sGender && rEntry.mktSegIncome == sIncome && rEntry.adType == pQuery.getAdType() && rEntry.dev == pQuery.getDevice()) {
						statsCalc.addValue(rEntry.costImpr);
						System.out.print(".");
					}
				}
			}
			else if (sGender != null) {
				System.out.print("#####STATSALLCRITERIA##### Gender");
				for(ImpressionRecord rEntry : impressionList) {
					if(rEntry.mktSegGender == sGender && rEntry.adType == pQuery.getAdType() && rEntry.dev == pQuery.getDevice()) {
						statsCalc.addValue(rEntry.costImpr);
						System.out.print(".");
					}
				}
			}
			else if (sAge != null) {
				System.out.print("#####STATSALLCRITERIA##### Age");
				for(ImpressionRecord rEntry : impressionList) {
					if(rEntry.mktSegAge == sAge && rEntry.adType == pQuery.getAdType() && rEntry.dev == pQuery.getDevice()) {
						statsCalc.addValue(rEntry.costImpr);
						System.out.print(".");
					}
				}
			}
			else if (sIncome != null) {
				System.out.print("#####STATSALLCRITERIA##### Income");
				for(ImpressionRecord rEntry : impressionList) {
					if(rEntry.mktSegIncome == sIncome && rEntry.adType == pQuery.getAdType() && rEntry.dev == pQuery.getDevice()) {
						statsCalc.addValue(rEntry.costImpr);
						System.out.print(".");
					}
				}
			}

			// System.out.println("#####STATSALLCRITERIA##### Items added: " + statsCalc.getN());
			if(statsCalc.getN() > 0) {
				returnVal.mean = statsCalc.getMean();
				returnVal.std = statsCalc.getStandardDeviation();
				returnVal.var = statsCalc.getVariance();
				returnVal.max = statsCalc.getMax();
				returnVal.min = statsCalc.getMin();
			}
			else {
				returnVal.mean = 0.000005;
				returnVal.std = 0;
				returnVal.var = 0;
				returnVal.max = 0.000005;
				returnVal.min = 0.000005;
			}

			return returnVal;
		}

		/**
		 * Method to save a file with the historic impressions bidding data. It basically opens the file stream and write each
		 * line using the toCsv method from the ImpressionRecord class.
		 */
		public void saveFile(){
			String workingDir = System.getProperty("user.dir");
			String fName = workingDir + "/BHFull.csv";
			String fLine;
			System.out.println("#####SAVEFILE##### Starting file save. Length:" + impressionList.size());
			try {
				FileWriter csvFw = new FileWriter(fName);
				csvFw.write("GameId,BidDay,CampId,AdType,Device,Publisher,Gender,MktGender,Income,MktIncome,Age,MktAge,BidCount,WinCount,TotalCost,CostImpr,LostCount" + System.lineSeparator());
				for(ImpressionRecord sRecord : impressionList){
					fLine = sRecord.toCsv();
					if(fLine != null) {csvFw.write(fLine + System.lineSeparator());}
				}
				csvFw.close();
			} catch(IOException ex){
				System.out.println("##### ERR Writing the CSV File #####");
				ex.printStackTrace();
			}
		}

		/**
		* This method reads the BHFull.csv file from the running directory to feed the impression bid strategy with
		* historic data from previous games.
		*
		* This methods was changed several times during the competition as per inconsistencies identified at runtime
		* that turned out to be a file encoding issue. It was fiexed using the .equals method from Strings.
		**/
		public void loadFile(){
			String workingDir = System.getProperty("user.dir");
			String fName = workingDir + "/BHFull.csv";
			String fLine;
			BufferedReader br;
			ImpressionRecord iRecord;
			AdType fAdType;
			Device fDevice;
			Gender fGender;
			Income fIncome;
			Age fAge;
			String readValue;

			try {
				File fileDir = new File(fName);
				BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fileDir), "UTF8"));

				fLine = in.readLine(); // Ignores the first line that contains the headers
				while ((fLine = in.readLine()) != null) {
					String[] fValues = fLine.split(",");
					// 0:GameId,1:BidDay,2:CampId,3:AdType,4:Device,5:Publisher,6:Gender,7:MktGender,8:Income,9:MktIncome,10:Age,11:MktAge,12:BidCount,13:WinCount,14:TotalCost,15:CostImpr,16:LostCount
					if(fValues[3].equals("text")) { fAdType = AdType.text; } else { fAdType = AdType.video; }
					if(fValues[4].equals("pc")) { fDevice = Device.pc; } else { fDevice = Device.mobile; }
					if(fValues[6].equals("male")) { fGender = Gender.male; } else { fGender = Gender.female; }
					if(fValues[8].equals("low")) { fIncome = Income.low; } else if(fValues[8].equals("medium")) { fIncome = Income.medium; }
					else if(fValues[8].equals("high")) { fIncome = Income.high; } else { fIncome = Income.very_high; }
					if(fValues[10].equals("Age_18_24")) { fAge = Age.Age_18_24; } else if(fValues[10].equals("Age_25_34")) { fAge = Age.Age_25_34; }
					else if(fValues[10].equals("Age_35_44")) { fAge = Age.Age_35_44; } else if(fValues[10].equals("Age_45_54")) { fAge = Age.Age_45_54; }
					else if(fValues[10].equals("Age_55_64")) { fAge = Age.Age_55_64; } else { fAge = Age.Age_65_PLUS; }
					iRecord = new ImpressionRecord(Integer.parseInt(fValues[0]), Integer.parseInt(fValues[1]), Integer.parseInt(fValues[2]),
							fAdType, fDevice, fValues[5].toString(), fGender, fIncome, fAge, Integer.parseInt(fValues[12]), Integer.parseInt(fValues[13]), Double.parseDouble(fValues[14]));
					impressionBidHistory.impressionList.add(iRecord);
					// System.out.println("#####CSVLINE##### - " + fValues[3] + "," + fValues[4] + "," + fValues[6] + "," + fValues[8] + "," + fValues[10]);
					// System.out.println("#####CSVLINE##### [] " + fAdType.toString() + "," + fDevice.toString() + "," + fGender.toString() + "," + fIncome.toString() + "," + fAge.toString());
				}
				System.out.println("#####LOADFILE##### Load file complete: " + impressionBidHistory.impressionList.size());
				in.close();
			}
			catch (IOException ex) {
				System.out.println("#####LOADFILE##### EXCEPTION WHEN READING THE CSV!!!!!!");
				ex.printStackTrace();
			}
		}
	}

	/**
	 * Class to store a single line of data coming from the AdNet Report.
	 * This class is used within Impression History to have a collection of historic records. This allows the
	 * calculation of statistics and other indices to take decisions during the game.
	 */
	private class ImpressionRecord {
		public int simId = 0;
		public int bidDay = 0;
		public int campId = 0;
		public AdType adType = AdType.text;
		public Device dev = Device.pc;
		public String pub = "";
		public Gender segGender = Gender.male;
		public MarketSegment mktSegGender = MarketSegment.MALE;
		public Income segIncome = Income.medium;
		public MarketSegment mktSegIncome = MarketSegment.HIGH_INCOME;
		public Age segAge = Age.Age_18_24;
		public MarketSegment mktSegAge = MarketSegment.YOUNG;
		public int bidCount = 0;
		public int winCount = 0;
		public double totalCost = 0;
		public double costImpr = 0;
		public int lostCount = 0;

		/**
		* Constructor using each property as parameter
		**/
		public ImpressionRecord(int pSimId, int pBidDay, int pCampId, AdType pAdType, Device pDev, String pPub, Gender pSegGender,
								Income pSegIncome, Age pSegAge, int pBidCount, int pWinCount, double pTotalCost){
			simId = pSimId;
			bidDay = pBidDay;
			campId = pCampId;
			adType = pAdType;
			dev = pDev;
			pub = pPub;
			segGender = pSegGender;
			mktSegGender = (segGender == Gender.male)? MarketSegment.MALE : MarketSegment.FEMALE;
			segIncome = pSegIncome;
			mktSegIncome = (segIncome == Income.high || segIncome == Income.very_high) ? MarketSegment.HIGH_INCOME : MarketSegment.LOW_INCOME;
			segAge = pSegAge;
			mktSegAge = (segAge == Age.Age_18_24 || segAge == Age.Age_25_34 || segAge == Age.Age_35_44) ? MarketSegment.YOUNG : MarketSegment.OLD;
			bidCount = pBidCount;
			winCount = pWinCount;
			totalCost = pTotalCost / 1000;
			costImpr = pTotalCost / pWinCount;
			lostCount = pBidCount - pWinCount;
		}

		/**
		* Constructor using only the Report entry as parameter. All values are extracted within this method.
		**/
		public ImpressionRecord(AdNetworkReportEntry pReportEntry){
			simId = startInfo.getSimulationID();
			bidDay = day -1;
			campId = pReportEntry.getKey().getCampaignId();
			adType = pReportEntry.getKey().getAdType();
			dev = pReportEntry.getKey().getDevice();
			pub = pReportEntry.getKey().getPublisher();
			segGender = pReportEntry.getKey().getGender();
			mktSegGender = (segGender == Gender.male)? MarketSegment.MALE : MarketSegment.FEMALE;
			segIncome = pReportEntry.getKey().getIncome();
			mktSegIncome = (segIncome == Income.high || segIncome == Income.very_high) ? MarketSegment.HIGH_INCOME : MarketSegment.LOW_INCOME;
			segAge = pReportEntry.getKey().getAge();
			mktSegAge = (segAge == Age.Age_18_24 || segAge == Age.Age_25_34 || segAge == Age.Age_35_44) ? MarketSegment.YOUNG : MarketSegment.OLD;
			bidCount = pReportEntry.getBidCount();
			winCount = pReportEntry.getWinCount();
			totalCost = pReportEntry.getCost() / 1000;
			costImpr = totalCost / winCount;
			lostCount = bidCount - winCount;
		}

		/**
		* Method to help with the file writing action by concatenating each value separated by a comma in a specified order.
		**/
		public String toCsv(){
			if(totalCost > 0.00000000001) {
				return simId + "," + bidDay + "," + campId + "," + adType.toString() + "," +
						dev.toString() + "," + pub + "," + segGender.toString() + "," + mktSegGender.toString() + "," +
						segIncome.toString() + "," + mktSegIncome.toString() + "," + segAge.toString() + "," +
						mktSegAge.toString() + "," + bidCount + "," + winCount + "," + totalCost + "," + costImpr + "," + lostCount;
			}
			else {
				return null;
			}
		}
	}

	/**
	* Structure to ease the manipulation of different statistical reports from a collection of values. Used to ease
	* the manipulation of org.apache.commons.math3 operations.
	**/
	private class BasicStatisticValues {
		public double mean;
		public double std;
		public double var;
		public double min;
		public double max;

		public String toString() {
			return "Mean: " + mean + ", Std: " + std + ", Var: " + var + ", Min: " + min + ", Max: " + max;
		}
	}


	public void campaignSaveFile(){
		String workingDir = System.getProperty("user.dir");
		String fName = workingDir + "\\CmpLog.csv";
		// TODO ALUN: fix so it doesn't print header every time
		//CSV file header
		final String FILE_HEADER = "game,id,dayStart,dayEnd,reachImps,targetSegment,videoCoef,mobileCoef," +
				"adxCost,targetedImps,untargetedImps,budget,revenue,profitEstimate,cmpBid,impressionTarget," +
				"uncorrectedProfitEstimate,costEstimate,estImpCost,estUcsCost,qualityChange,estQualityChange," +
				"ucsCost,estCostAcc,estProfitAcc,uncorrectedProffitAcc,estQualityChangeAcc,impTargetFulfillment," +
				"bidVs2ndRatio,profit,profitPerImpression,reachFulfillment,estUcsCostAcc";
		try {
			FileWriter csvFw = new FileWriter(fName, true);
			// TODO: include FILEHEADER when writing new file
			//csvFw.write(FILE_HEADER + System.lineSeparator());

			//Add a new line separator after the header
			for (Map.Entry<Integer, CampaignData> entry : myCampaigns.entrySet()) {
				CampaignData campaign = entry.getValue();
				csvFw.append(campaign.toWrite() + System.lineSeparator());
			}

			csvFw.close();
			System.out.println("Printed campaign csv successfully");
		} catch(IOException ex){
			System.out.println("##### ERR Writing the CSV File #####");
		}
	}

}
