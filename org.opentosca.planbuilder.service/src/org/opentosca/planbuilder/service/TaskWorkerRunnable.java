package org.opentosca.planbuilder.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.Header;
import org.apache.http.entity.mime.MinimalField;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.opentosca.core.model.csar.CSARContent;
import org.opentosca.core.model.csar.id.CSARID;
import org.opentosca.planbuilder.model.plan.BuildPlan;
import org.opentosca.planbuilder.service.model.PlanGenerationState;
import org.opentosca.planbuilder.service.model.PlanGenerationState.PlanGenerationStates;
import org.opentosca.util.http.service.IHTTPService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Copyright 2015 IAAS University of Stuttgart <br>
 * <br>
 * 
 * @author Kalman Kepes - kepeskn@studi.informatik.uni-stuttgart.de
 *
 */
public class TaskWorkerRunnable implements Runnable {
	
	private PlanGenerationState state;
	
	final private static Logger LOG = LoggerFactory.getLogger(TaskWorkerRunnable.class);
	
	
	public TaskWorkerRunnable(PlanGenerationState state) {
		this.state = state;
	}
	
	public PlanGenerationState getState() {
		return this.state;
	}
	
	@Override
	public void run() {
		
		LOG.debug("Starting to download CSAR");
		this.state.currentState = PlanGenerationState.PlanGenerationStates.CSARDOWNLOADING;
		// download csar
		IHTTPService openToscaHttpService = ServiceRegistry.getHTTPService();
		
		if (openToscaHttpService == null) {
			this.state.currentState = PlanGenerationStates.CSARDOWNLOADFAILED;
			this.state.currentMessage = "Couldn't aquire internal HTTP Service to download CSAR";
			LOG.error("Couldn't aquire internal HTTP Service to download CSAR");
			return;
		}
		
		InputStream csarInputStream = null;
		try {
			LOG.debug("Downloading CSAR " + this.state.getCsarUrl());
			HttpResponse csarResponse = openToscaHttpService.Get(this.state.getCsarUrl().toString());
			csarInputStream = csarResponse.getEntity().getContent();
		} catch (ClientProtocolException e) {
			this.state.currentState = PlanGenerationStates.CSARDOWNLOADFAILED;
			this.state.currentMessage = "Couldn't download CSAR";
			LOG.error("Couldn't download CSAR");
			return;
		} catch (IOException e) {
			this.state.currentState = PlanGenerationStates.CSARDOWNLOADFAILED;
			this.state.currentMessage = "Couldn't download CSAR";
			LOG.error("Couldn't download CSAR");
			return;
		}
		
		if (csarInputStream == null) {
			this.state.currentState = PlanGenerationStates.CSARDOWNLOADFAILED;
			this.state.currentMessage = "Couldn't download CSAR";
			LOG.error("Couldn't download CSAR");
			return;
		}
		
		this.state.currentState = PlanGenerationStates.CSARDOWNLOADED;
		this.state.currentMessage = "Downloaded CSAR";
		LOG.debug("CSAR download finished");
		
		// generate plan (assumption: the send csar contains only one
		// topologytemplate => only one buildPlan will be generated
		LOG.debug("Storing CSAR");
		CSARID csarId = Util.storeCSAR(System.currentTimeMillis() + ".csar", csarInputStream);
		
		if (csarId != null) {
			this.state.currentState = PlanGenerationStates.PLANGENERATING;
			this.state.currentMessage = "Generating Plan";
			LOG.debug("Starting to generate Plan");
		} else {
			this.state.currentState = PlanGenerationStates.CSARDOWNLOADFAILED;
			this.state.currentMessage = "Couldn't store CSAR";
			LOG.error("Couldn't store CSAR");
			Util.deleteCSAR(csarId);
			return;
		}
		
		List<BuildPlan> buildPlans = Util.startPlanBuilder(csarId);
		
		if (buildPlans.size() <= 0) {
			this.state.currentState = PlanGenerationStates.PLANGENERATIONFAILED;
			this.state.currentMessage = "No plans could be generated";
			Util.deleteCSAR(csarId);
			LOG.error("No plans could be generated");
			return;
		}
		
		// write to tmp dir, only generating one plan
		File planTmpFile = Util.writePlan2TmpFolder(buildPlans.get(0));
		
		this.state.currentState = PlanGenerationStates.PLANGENERATED;
		this.state.currentMessage = "Stored and generated Plan";
		LOG.debug("Stored and generated Plan");
		
		// send plan back
		MultipartEntity mpEntity = new MultipartEntity();
		
		try {
			mpEntity.addPart("planName", new StringBody(planTmpFile.getName()));
			mpEntity.addPart("planType", new StringBody("http://docs.oasis-open.org/tosca/ns/2011/12/PlanTypes/BuildPlan"));
			mpEntity.addPart("planLanguage", new StringBody("http://docs.oasis-open.org/wsbpel/2.0/process/executable"));
		} catch (UnsupportedEncodingException e1) {
			this.state.currentState = PlanGenerationStates.PLANGENERATIONFAILED;
			this.state.currentMessage = "Couldn't generate Upload request to PLANPOSTURL";
			Util.deleteCSAR(csarId);
			LOG.error("Couldn't generate Upload request to PLANPOSTURL");
			return;
		}
		
		FileBody bin = new FileBody(planTmpFile);
		ContentBody cb = (ContentBody) bin;
		mpEntity.addPart("file", cb);
		
		try {
			
			this.state.currentState = PlanGenerationStates.PLANSENDING;
			this.state.currentMessage = "Sending Plan";
			LOG.debug("Sending Plan");
			
			HttpResponse uploadResponse = openToscaHttpService.Post(this.state.getPostUrl().toString(), mpEntity);
			if (uploadResponse.getStatusLine().getStatusCode() >= 300) {
				// we assume ,if the status code ranges from 300 to 5xx , that
				// an error occured
				this.state.currentState = PlanGenerationStates.PLANSENDINGFAILED;
				this.state.currentMessage = "Couldn't send plan. Server send status " + uploadResponse.getStatusLine().getStatusCode();
				Util.deleteCSAR(csarId);
				LOG.error("Couldn't send plan. Server send status " + uploadResponse.getStatusLine().getStatusCode());
				return;
			} else {
				// still need to send the parameters
				
				// fetch location header from plan upload response
				org.apache.http.Header locationHeader = uploadResponse.getHeaders("Location")[0];
				String planLocation = "";
				
				if (locationHeader == null) {
					// no location header sent back, construct inputParam url
					planLocation = this.state.getPostUrl() + planTmpFile.getName();
				} else {
					planLocation = locationHeader.getValue();
				}
				
				BuildPlan buildPlan = buildPlans.get(0);
				/*
				 * http://localhost:8080/winery/servicetemplates/http%253A%252F%252F
				 * example
				 * .com%252Fhello/HelloAppTest/plans/HelloAppTest_buildPlan
				 * .zip/inputparameters/?name=test&required=on&type=String *
				 */
				
				// post the parameters
				for (String inputParam : buildPlan.getWsdl().getInputMessageLocalNames()) {
					String inputParamPostUrl = planLocation + "/inputparameters/";
					
					List<NameValuePair> params = new ArrayList<NameValuePair>();
					params.add(Util.createNameValuePair("name", inputParam));
					params.add(Util.createNameValuePair("type", "String"));
					params.add(Util.createNameValuePair("required", "on"));
					
					UrlEncodedFormEntity encodedForm = new UrlEncodedFormEntity(params);
					
					HttpResponse inputParamPostResponse = openToscaHttpService.Post(inputParamPostUrl, encodedForm);
					if (inputParamPostResponse.getStatusLine().getStatusCode() >= 300) {
						this.state.currentState = PlanGenerationStates.PLANSENDINGFAILED;
						this.state.currentMessage = "Couldn't set inputParameters. Setting InputParam (postURL: " + inputParamPostUrl + ") " + inputParam + " failed, Service for Plan Upload sent statusCode " + inputParamPostResponse.getStatusLine().getStatusCode();
						Util.deleteCSAR(csarId);
						LOG.error("Couldn't set inputParameters. Setting InputParam (postURL: " + inputParamPostUrl + ") " + inputParam + " failed, Service for Plan Upload sent statusCode " + inputParamPostResponse.getStatusLine().getStatusCode());
						return;
					}
					LOG.debug("Sent inputParameter " + inputParam);
				}
				
				for (String outputParam : buildPlan.getWsdl().getOuputMessageLocalNames()) {
					String outputParamPostUrl = planLocation + "/outputparameters/";
					
					List<NameValuePair> params = new ArrayList<NameValuePair>();
					params.add(Util.createNameValuePair("name", outputParam));
					params.add(Util.createNameValuePair("type", "String"));
					params.add(Util.createNameValuePair("required", "on"));
					
					UrlEncodedFormEntity encodedForm = new UrlEncodedFormEntity(params);
					
					HttpResponse outputParamPostResponse = openToscaHttpService.Post(outputParamPostUrl, encodedForm);
					if (outputParamPostResponse.getStatusLine().getStatusCode() >= 300) {
						this.state.currentState = PlanGenerationStates.PLANSENDINGFAILED;
						this.state.currentMessage = "Couldn't set outputParameters. Setting OutputParam (postURL: " + outputParamPostUrl + ") " + outputParam + " failed, Service for Plan Upload sent statusCode " + outputParamPostResponse.getStatusLine().getStatusCode();
						Util.deleteCSAR(csarId);
						LOG.error("Couldn't set outputParameters. Setting OutputParam (postURL: " + outputParamPostUrl + ") " + outputParam + " failed, Service for Plan Upload sent statusCode " + outputParamPostResponse.getStatusLine().getStatusCode());
						return;
					}
					LOG.debug("Sent outputParameter " + outputParam);
				}
				
				this.state.currentState = PlanGenerationStates.PLANSENT;
				this.state.currentMessage = "Sent plan. Everythings okay";
				Util.deleteCSAR(csarId);
				LOG.debug("Sent plan. Everythings okay");
				return;
			}
		} catch (ClientProtocolException e) {
			this.state.currentState = PlanGenerationStates.PLANSENDINGFAILED;
			this.state.currentMessage = "Couldn't send plan.";
			Util.deleteCSAR(csarId);
			LOG.error("Couldn't send plan.");
			return;
		} catch (IOException e) {
			this.state.currentState = PlanGenerationStates.PLANSENDINGFAILED;
			this.state.currentMessage = "Couldn't send plan.";
			Util.deleteCSAR(csarId);
			LOG.error("Couldn't send plan.");
			return;
		}
	}
}