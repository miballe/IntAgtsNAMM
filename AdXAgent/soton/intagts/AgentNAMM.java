//package soton.intagts;

import edu.umich.eecs.tac.props.Ad;
import edu.umich.eecs.tac.props.BankStatus;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
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
import se.sics.isl.transport.Transportable;
import se.sics.tasim.aw.Agent;
import se.sics.tasim.aw.Message;
import se.sics.tasim.props.SimulationStatus;
import se.sics.tasim.props.StartInfo;
import tau.tac.adx.ads.properties.AdType;
import tau.tac.adx.demand.CampaignStats;
import tau.tac.adx.devices.Device;
import tau.tac.adx.props.AdxBidBundle;
import tau.tac.adx.props.AdxQuery;
import tau.tac.adx.props.PublisherCatalog;
import tau.tac.adx.props.PublisherCatalogEntry;
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

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math3.stat.*;

/**
 * 
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
	/*
	 * the bidBundle to be sent daily to the AdX
	 */
	private AdxBidBundle bidBundle;

	/*
	 * The current bid level for the user classification service
	 */
	double ucsBid;

	/*
	 * The targeted service level for the user classification service
	 */
	int ucsTargetLevel;
	double quality = 1;
	private PerformanceData performanceData;
	/*
	 *  The bid campaign bid we send.
	 *  Note it is not reset each day on purpose so there is a default value in case we fail to calculate a bid in time.
	 */
	double cmpBid;

	/*
	 * current day of simulation
	 */
	private int day;
	private String[] publisherNames;
	private CampaignData currCampaign;

    /**
     * This property is the instance of a new NAMM class to keep record of all Ad-Net reports during a game execution.
     * The idea is to use historic data as reference to estimate new bid prices or provide values for some strategies.
     */
    private ImpressionHistory impressionBidHistory;

	public AgentNAMM() {
        campaignReports = new LinkedList<CampaignReport>();
        impressionBidHistory = new ImpressionHistory();
	}




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
			this.log.log(Level.SEVERE,
					"Exception thrown while trying to parse message." + e);
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
		System.out.println("!!!!!!!!!!!!!!!!!!" + startInfo);
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
	}

	/**
	 * On day n ( > 0) a campaign opportunity is announced to the competing
	 * agents. The campaign starts on day n + 2 or later and the agents may send
	 * (on day n) related bids (attempting to win the campaign). The allocation
	 * (the winner) is announced to the competing agents during day n + 1.
	 */
	private void handleICampaignOpportunityMessage(
			CampaignOpportunityMessage com) {
		System.out.println("WORKING!");
			day = com.getDay();

		// For campaigns that finished yesterday set performance metrics.
		for (Map.Entry<Integer, CampaignData> entry : myCampaigns.entrySet()) {
			CampaignData campaign = entry.getValue();
			System.out.println("working here");
			if ((entry.getValue().dayEnd == day - 1)) {
				System.out.println("...");
				long imps = (long)(campaign.stats.getOtherImps() + campaign.stats.getTargetedImps());
				double revenue = campaign.budget * ERRcalc(campaign, imps);
				campaign.setRevenue(revenue);
				campaign.setProfit();
				campaign.setEstCostAcc();
				campaign.setUncorrectedProfitAcc();
				campaign.setEstProfitAcc();
				campaign.setImpTargetFulfillment();
				campaign.setProfitPerImpression();
				campaign.setReachFulfillment();
				campaign.setBidVs2ndRatio();
				campaign.setQualityChange();
				campaign.setEstQualityChangeAcc();
				campaign.setEstUcsCostAcc();

				// Update performance data
				performanceData.updateData(campaign);
				//todo add estimate quality change

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

				/*System.out.printf(
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
		System.out.println("working afterwards?");


		pendingCampaign = new CampaignData(com);
		System.out.println("Day " + day + ": Campaign opportunity - " + pendingCampaign);
		System.out.println("working afterwards??");

		/*
		*  ALUN: Decide which of the 4 campaign strategies to use
		*/
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
		System.out.print("Day " + day + ": Campaign - Bid: " + (long)(cmpBid*1000));
		// If bid is too high, just bid the maximum value.
		if (cmpBid >= bidTooHigh(cmpimps, 95)) {
			cmpBid = 0.001 * cmpimps * adNetworkDailyNotification.getQualityScore() - 0.001;
			System.out.print(" " + (long)(cmpBid*1000) + "-too high!");
		}
		// If bid is too low, bid the "minimum value"
		double lowBid = bidTooLow(cmpimps, 95);
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
		System.out.println("Day " + day + ": Campaign - Total budget bid (millis): " + (long)(cmpBid*1000));

		/*
		 * Adjust ucs bid s.t. target level is achieved. Note: The bid for the
		 * user classification service is piggybacked
		 */
		// TODO: Nikola UCS bid calculation here
		Random random = new Random();
		if (adNetworkDailyNotification != null) {
			double ucsLevel = adNetworkDailyNotification.getServiceLevel();
			ucsBid = 0.1 + random.nextDouble()/10.0;
			System.out.println("Day " + day + ": ucs level reported: " + ucsLevel);
		} else {
			System.out.println("Day " + day + ": Initial ucs bid is " + ucsBid);
		}

		/* Note: Campaign bid is in millis */
		AdNetBidMessage bids = new AdNetBidMessage(ucsBid, pendingCampaign.id, (long)(cmpBid*1000));
		sendMessage(demandAgentAddress, bids);
		/* TODO FIx bug where day 0 isn't bid for
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
	 *
	 */
	protected void sendBidAndAds() {

		/**
		 * TODO: MB, Remove this block for final version
		 */
		// FileWriter csvWriter;
		// try{
		// csvWriter = new FileWriter("c:\\temp\\queries.csv");
		// StringBuilder csvLine = new StringBuilder();


		bidBundle = new AdxBidBundle();

		int dayBiddingFor = day + 1;

		/**
		 *  A fixed random bid, for all queries of the campaign
		 * Note: bidding per 1000 imps (CPM) - no more than average budget
		 * revenue per imp
		 */

		double rbid = 10000.0;

		/**
		 * add bid entries w.r.t. each active campaign with remaining contractedcmpBidMillis
		 * impressions.
		 *
		 * for now, a single entry per active campaign is added for queries of
		 * matching target segment.
		 */

		if ((dayBiddingFor >= currCampaign.dayStart)
				&& (dayBiddingFor <= currCampaign.dayEnd)
				&& (currCampaign.impsTogo() > 0)) {

			int entCount = 0;

			/**
			 * TODO: MB, Consider overachieving campaigns when quality < 1
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
					bidBundle.addQuery(query, rbid, new Ad(null), currCampaign.id, 1);

					//csvLine.append(query.getPublisher() + "," + query.getTransportName() + "," + query.getAdType() + "," + query.getDevice() + "," + query.getMarketSegments() + "," + currCampaign.id + '\n');
				}
			}

			double impressionLimit = currCampaign.impsTogo();
			double budgetLimit = currCampaign.budget;
			bidBundle.setCampaignDailyLimit(currCampaign.id,
					(int) impressionLimit, budgetLimit);

			System.out.println("Day " + day + " Bid Bundle: Updated " + entCount
					+ " Bid Bundle entries for Campaign id " + currCampaign.id);
			log.log(Level.ALL, "## Bid Bundle ##; currCampaign: " + currCampaign.id + "; " + (long)currCampaign.budget);

			// csvWriter.write(csvLine.toString());

		}

		/**
		 * TODO, MB Delete these lines for CSV file
		 */
		// csvWriter.flush();
		// csvWriter.close();

		if (bidBundle != null) {
			System.out.println("Day " + day + ": Sending BidBundle");
			sendMessage(adxAgentAddress, bidBundle);
		}

		// }	catch(IOException e) {
		// 	e.printStackTrace();
		// }
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
            impressionBidHistory.impressionList.add(new ImpressionRecord(repEntry));
            //System.out.println("#####ADNETREPORTENTRY#####" + repEntry.toString());
        }
        /*System.out.println("#####BIDIMPRHISTORY##### NItems" + impressionBidHistory.impressionList.size() +
                            ", Male mean: " + impressionBidHistory.getMeanPerSegmentGender(Gender.male) +
                            ", Female mean: " + impressionBidHistory.getMeanPerSegmentGender(Gender.female));*/
	}

	@Override
	protected void simulationSetup() {

		day = 0;
		bidBundle = new AdxBidBundle();

		/* initial bid between 0.1 and 0.2 */
		ucsBid = 0.2;

		myCampaigns = new HashMap<Integer, CampaignData>();
		log.fine("AdNet " + getName() + " simulationSetup");
	}

	@Override
	protected void simulationFinished() {
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

	private class CampaignData {
		/* campaign attributes as set by server */
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
		}

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
		}

		@Override
		public String toString() {
			return "Campaign ID " + id + ": " + "day " + dayStart + " to "
					+ dayEnd + " " + targetSegment + ", reach: " + reachImps
					+ " coefs: (v=" + videoCoef + ", m=" + mobileCoef + ")";
		}

		public String toWrite() {
			return id + "," + dayStart + "," + dayEnd + "," + reachImps + "," + targetSegment + "," + videoCoef + ","
				+ mobileCoef + "," + stats.getCost() + "," + stats.getTargetedImps() + "," + stats.getOtherImps() + ","
				+ budget + "," + revenue  + "," + profitEstimate  + "," + cmpBid + "," + impressionTarget  + "," +
				uncorrectedProfitEstimate + "," + costEstimate + "," + costEstimate  + "," + estImpCost  + "," +
				estUcsCost  + "," + qualityChange  + "," + estQualityChange  + "," + ucsCost  + "," + estCostAcc
				+ "," +estProfitAcc  + "," + uncorrectedProfitAcc + "," + estQualityChangeAcc + "," + impTargetFulfillment
				+ "," + bidVs2ndRatio + "," + profit + "," + profitPerImpression + "," + reachFulfillment  + "," +
				estUcsCostAcc;
		}

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

		// Calculates an estimate for impression targets (and profit) to maximise estimated profit.
		// Considers the effect of short term cost of the campaign, long term effect of quality change and inaccuracies in previous predictions.
		private void setImpressionTargets() {
			long target = 0;
			double estProfit = -99999, ERR = 0, estQuality = 0, estCost = 0;
			// Consider a range of possible impression targets
			for (double multiplier = 0.6; multiplier <= 2; multiplier+= 0.02){ // loop over range of impression targets
				long tempTarget = (long)(this.reachImps*multiplier);

				double currentQuality = adNetworkDailyNotification.getQualityScore();
				double lRate = 0.6, Budget;
				ERR = ERRcalc(this, target);
				double tempEstQuality = (1 - lRate)*currentQuality + lRate*ERR;

				// Decide which impression target is most cost efficient
				// todo need to change this.budget to a historical average budget per impression
				// so this can be used to set the budget bid for. (but if budget already set then use it)
				if (this.budget != 0){ Budget = this.budget; }
				else Budget = 0; //TODO mean budget/impression from past * impressions;
				double tempEstCost = campaignCost(this, tempTarget, false);
				double tempEstProfit = Budget * ERR + qualityEffect(this, estQuality) - tempEstCost;
				if (tempEstProfit > estProfit) {
					target = tempTarget;
					estProfit = tempEstProfit;
					estCost = tempEstCost;
					estQuality = tempEstQuality;
				}
			}

			// Save ucs cost and impression cost estimates
			campaignCost(this, target, true);
			System.out.println("Q: " + adNetworkDailyNotification.getQualityScore() + " estQ: " + estQuality + " ERR: " + ERR);
			this.estQualityChange = estQuality - adNetworkDailyNotification.getQualityScore();

			// Factor in any bias we may have (adjust for difference in prediction and result)
			// This multiplier is highly subject to random noise at the start and should incorporate historic data to
			// help smooth this: TODO historic data
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
			// error factor
			double profitError = cumProfit / cumProfitEstimate;
			uncorrectedProfitEstimate = estProfit - qualityEffect(this,estQuality);
			profitEstimate = uncorrectedProfitEstimate * profitError;
			impressionTarget = target;
			costEstimate = estCost;

			/* System.out.println("ESTIMATED PROFIT: " + estProfit + " | target: " + target + " | Est.cmp cost: " +
					campaignCost(this,target/(this.dayEnd-this.dayStart), false) + " | Est.Quality effect: "
					+ qualityEffect(this, estQuality) + " | Est.ERR: " + ERR + " | cmpRevenue: " + this.budget*ERR
			+ " | uncorrected profit estimate: " + uncorrectedProfitEstimate); */

		}

	}



	/**
	 *  User defined methods
	 */

	/*
	 * ALUN: different methods for each campaign strategy
     */

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
		// TODO Historic Data
			// stores historic data about campaigns and classifies them as either profitable or non-profitable.
			// Uses some classifier algorithm to analyse the probability of a bid giving us a profitable campaign.
			// Sets a minimum bid at X % chance not profitable.
			// X is a overly conservative value (low ~ 5%) be
			// and because maximum can move down but not up.
		// currently just evaluates the reserve price
		double bidLow = cmpimps * 0.0001 / adNetworkDailyNotification.getQualityScore();
		System.out.println(" Min: " + (long)(bidLow*1000));
		return bidLow;
	}

	/**
	* Goes through all previously successful bids, models it as a normal distribution (which it may not be)
	* And evaluates through a t-test a bid with the required failure confidence to consider it too high to succeed.
	*/
	private double bidTooHigh(long cmpimps, int percentFailure) {
		// At the moment models as uniform distribution.
		// TODO Historic Data
			// Stores historic data about campaign bids, classifying them as successful or non-sucessful.
			// Uses some classifier algorithm to analyse the probability of a bid being successful.
			// Sets a maximum bid at X % chance to succeed.
			// X is a overly conservative value (low ~ 5%) because you are not taking into account environmental factors
			// and because maximum can move down but not up.
		double bidHigh = (0.001*cmpimps*percentFailure)/100;
		// Make sure bid is still below maximum price.
		double bidMax = 0.001 * cmpimps * adNetworkDailyNotification.getQualityScore();
		if (bidHigh >= bidMax) bidHigh = bidMax;
		System.out.print(" MaxBid: " + (long)(1000*bidMax) + " MinMax: " + (long)(1000*bidHigh));
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
		if (myCampaigns.size() > 1) {
			for (Map.Entry<Integer, CampaignData> entry : myCampaigns.entrySet()) {
				if (entry.getValue().dayStart != 1) {
					totalCostPerImp += entry.getValue().budget / entry.getValue().reachImps;
				}
			}
			bidFactor = (random.nextInt(40)/100) + 0.8;
			bid = pendingCampaign.reachImps * totalCostPerImp / (myCampaigns.size() -1) * bidFactor;
		}
		else bid = (double)random.nextInt(pendingCampaign.reachImps.intValue())/1000; //Random bid initially

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
		double bid =  campaignProfitStrategy() * Math.pow(adNetworkDailyNotification.getQualityScore(),2); //TODO Historic Data
		System.out.println("Day " + day + ": Campaign - Quality Recovery Strategy");
		/*
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
	 * TODO: Evaluate how to base these weights.
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
		double historicDailyIncome = 1;// TODO machine learning
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
				// todo allow flexibility with daily impressions
					// e.g. increase impression target close to deadline
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


	/*
	 *  Manu: Impression cost estimate
	 *  This method takes an impression target as an input and evaluates the estimated cost to achieve that value given
	 *  the day and UCS target.
	 *  Again later it will be useful to be able to estimate costs for a range of impressions to choose a local optimum.
	 *  May include functionality to recall past actual costs / estimates.
	 *  USE: use this function for campaign bids by evaluating the best impression/UCS combination then summing over
	 *  all days of the prospective campaign to evaluate total cost to complete campaign.
	 */
	private double impressionCostEstimate(long impTarget, long day, int ucsTargetLevel) {
		// TODO;
		// You can now access impression targets from campaign data;
		// e.g. pendingCampaign.impressionTarget
		return 0.0006 * impTarget; // default value 0.0006 per impression
	}

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
		return 0;
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
	 */
	private double ImpressionBidCalculator(double impressionTarget, AdxQuery query){
		// TODO;
		// You can now access impression targets from campaign data;
		// e.g. pendingCampaign.impressionTarget
		return 0;
	}

    /**
     * Class to keep a record of all historic bid results coming from the server. This ie useful for
     * future estimates and support in general the campaigns and impressions bidding strategy.
     */
    private class ImpressionHistory {
        // Main collection. Record list of type ImpressionRecord defined below
        public List<ImpressionRecord> impressionList;

        /**
         * Constructor method. Basically initializes the ArrayList at the beginning of the game when
         * an instance of AgentNAMM is
         */
        public ImpressionHistory(){
            impressionList = new ArrayList<ImpressionRecord>();
        }

        public double getMeanPerSegmentGender(Gender sGender){
            DescriptiveStatistics statsCalc = new DescriptiveStatistics();
            double mean = 0;

            for(ImpressionRecord rEntry : impressionList) {
                if(rEntry.segGender == sGender) {
                    statsCalc.addValue(rEntry.costImpr);
                }
            }
            mean = statsCalc.getMean();
            System.out.println("#####STATMEAN##### Historic mean per gender " + sGender + ":" + mean);
            return mean;
        }

        public void saveFile(){
            String workingDir = System.getProperty("user.dir");
            String fName = workingDir + "\\BH" + System.currentTimeMillis() + ".csv";
            try {
                FileWriter csvFw = new FileWriter(fName);
                csvFw.write("BidDay,CampId,AdType,Device,Publisher,Gender,Income,Age,BidCount,WinCount,CostImpr" + System.lineSeparator());
                for(ImpressionRecord sRecord : impressionList){
                    csvFw.write(sRecord.toString() + System.lineSeparator());
                }
                csvFw.close();
            } catch(IOException ex){
                System.out.println("##### ERR Writing the CSV File #####");
            }
        }
    }

    /**
     * Class to store a single line of data coming from the AdNet Report.
     * This class is used within Impression History to have a collection of historic records. This allows the
     * calculation of statistics and other indices to take decisions during the trading.
     */
    private class ImpressionRecord {
        public int bidDay = 0;
        public int campId = 0;
        public AdType adType = AdType.text;
        public Device dev = Device.pc;
        public String pub = "";
        public Gender segGender = Gender.male;
        public Income segIncome = Income.medium;
        public Age segAge = Age.Age_18_24;
        public int bidCount = 0;
        public int winCount = 0;
        public double costImpr = 0;

        public ImpressionRecord(int pBidDay, int pCampId, AdType pAdType, Device pDev, String pPub, Gender pSegGender,
                                Income pSegIncome, Age pSegAge, int pBidCount, int pWinCount, double pCostImpr){
            bidDay = pBidDay;
            campId = pCampId;
            adType = pAdType;
            dev = pDev;
            pub = pPub;
            segGender = pSegGender;
            segIncome = pSegIncome;
            segAge = pSegAge;
            bidCount = pBidCount;
            winCount = pWinCount;
            costImpr = pCostImpr;
        }

        public ImpressionRecord(AdNetworkReportEntry pReportEntry){
            bidDay = day -1;
            campId = pReportEntry.getKey().getCampaignId();
            adType = pReportEntry.getKey().getAdType();
            dev = pReportEntry.getKey().getDevice();
            pub = pReportEntry.getKey().getPublisher();
            segGender = pReportEntry.getKey().getGender();
            segIncome = pReportEntry.getKey().getIncome();
            segAge = pReportEntry.getKey().getAge();
            bidCount = pReportEntry.getBidCount();
            winCount = pReportEntry.getWinCount();
            costImpr = pReportEntry.getCost();
        }

        public String toString(){
            return bidDay + "," + campId + "," + adType.toString() + "," + dev.toString() + "," + pub + "," +
                    segGender.toString() + "," + segIncome.toString() + "," + segAge.toString() + "," +
                    bidCount + "," + winCount + "," + costImpr;
        }
    }


	public void campaignSaveFile(){
		String workingDir = System.getProperty("user.dir");
		String fName = workingDir + "\\Cmp" + System.currentTimeMillis() + ".csv";

		//CSV file header
		final String FILE_HEADER = "id,dayStart,dayEnd,reachImps,targetSegment,videoCoef,mobileCoef," +
				"adxCost,targetedImps,untargetedImps,budget,revenue,profitEstimate,cmpBid,impressionTarget," +
				"uncorrectedProfitEstimate,costEstimate,estImpCost,estUcsCost,qualityChange,estQualityChange," +
				"ucsCost,estCostAcc,estProfitAcc,uncorrectedProfficAcc,estQualityChangeAcc,impTargetFulfillment," +
				"bidVs2ndRatio,profit,profitPerImpression,reachFulfillment,estUcsCostAcc";

		try {
			FileWriter csvFw = new FileWriter(fName, true);
			csvFw.write(FILE_HEADER + System.lineSeparator());

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
