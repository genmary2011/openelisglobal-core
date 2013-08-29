/**
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/ 
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 * 
 * The Original Code is OpenELIS code.
 * 
 * Copyright (C) ITECH, University of Washington, Seattle WA.  All Rights Reserved.
 *
 */
package us.mn.state.health.lims.reports.action.implementation;

import java.sql.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.validator.GenericValidator;

import us.mn.state.health.lims.analysis.dao.AnalysisDAO;
import us.mn.state.health.lims.analysis.daoimpl.AnalysisDAOImpl;
import us.mn.state.health.lims.analysis.valueholder.Analysis;
import us.mn.state.health.lims.common.action.BaseActionForm;
import us.mn.state.health.lims.common.services.PatientService;
import us.mn.state.health.lims.common.services.StatusService;
import us.mn.state.health.lims.common.services.StatusService.AnalysisStatus;
import us.mn.state.health.lims.common.util.DateUtil;
import us.mn.state.health.lims.dictionary.dao.DictionaryDAO;
import us.mn.state.health.lims.dictionary.daoimpl.DictionaryDAOImpl;
import us.mn.state.health.lims.dictionary.valueholder.Dictionary;
import us.mn.state.health.lims.organization.dao.OrganizationDAO;
import us.mn.state.health.lims.organization.daoimpl.OrganizationDAOImpl;
import us.mn.state.health.lims.organization.valueholder.Organization;
import us.mn.state.health.lims.patient.valueholder.Patient;
import us.mn.state.health.lims.reports.action.implementation.reportBeans.TestSegmentedExportBean;
import us.mn.state.health.lims.requester.dao.SampleRequesterDAO;
import us.mn.state.health.lims.requester.daoimpl.RequesterTypeDAOImpl;
import us.mn.state.health.lims.requester.daoimpl.SampleRequesterDAOImpl;
import us.mn.state.health.lims.requester.valueholder.SampleRequester;
import us.mn.state.health.lims.result.dao.ResultDAO;
import us.mn.state.health.lims.result.daoimpl.ResultDAOImpl;
import us.mn.state.health.lims.result.valueholder.Result;
import us.mn.state.health.lims.sample.dao.SampleDAO;
import us.mn.state.health.lims.sample.daoimpl.SampleDAOImpl;
import us.mn.state.health.lims.sample.valueholder.Sample;
import us.mn.state.health.lims.samplehuman.dao.SampleHumanDAO;
import us.mn.state.health.lims.samplehuman.daoimpl.SampleHumanDAOImpl;
import us.mn.state.health.lims.sampleitem.dao.SampleItemDAO;
import us.mn.state.health.lims.sampleitem.daoimpl.SampleItemDAOImpl;
import us.mn.state.health.lims.sampleitem.valueholder.SampleItem;

public class HaitiLNSPExportReport extends CSVExportReport{

	private DateRange dateRange;
	private String lowDateStr;
	private String highDateStr;
	private static final SampleHumanDAO sampleHumanDAO = new SampleHumanDAOImpl();
	private static final SampleItemDAO sampleItemDAO = new SampleItemDAOImpl();
	private static final AnalysisDAO analysisDAO = new AnalysisDAOImpl();
	private static final ResultDAO resultDAO = new ResultDAOImpl();
	private static final DictionaryDAO dictionaryDAO = new DictionaryDAOImpl();
	private static final SampleDAO sampleDAO = new SampleDAOImpl();
	private static final SampleRequesterDAO sampleRequesterDAO = new SampleRequesterDAOImpl();
	private static final OrganizationDAO organizationDAO = new OrganizationDAOImpl();
	private static final long ORGANIZTION_REFERRAL_TYPE_ID;
	private List<TestSegmentedExportBean> testExportList;

	static{
		String orgTypeId = new RequesterTypeDAOImpl().getRequesterTypeByName("organization").getId();
		ORGANIZTION_REFERRAL_TYPE_ID = orgTypeId == null ? -1L : Long.parseLong(orgTypeId);
	}

	@Override
	public void initializeReport(BaseActionForm dynaForm){
		super.initializeReport();

		errorFound = false;

		lowDateStr = dynaForm.getString("lowerDateRange");
		highDateStr = dynaForm.getString("upperDateRange");
		dateRange = new DateRange(lowDateStr, highDateStr);

		createReportParameters();

		errorFound = !validateSubmitParameters();
		if(errorFound){
			return;
		}

		createReportItems();
	}

	private void createReportItems(){
		testExportList = new ArrayList<TestSegmentedExportBean>();
		List<Sample> orderList = sampleDAO.getSamplesReceivedInDateRange(lowDateStr, highDateStr);

		for(Sample order : orderList){
			getResultsForOrder(order);
		}
	}

	private void getResultsForOrder(Sample order){
		Patient patient = sampleHumanDAO.getPatientForSample(order);
		List<SampleRequester> requesterList = sampleRequesterDAO.getRequestersForSampleId(order.getId());
		Organization requesterOrganization = null;
		
		for(SampleRequester requester : requesterList){
			if(requester.getRequesterTypeId() == ORGANIZTION_REFERRAL_TYPE_ID){
				requesterOrganization = organizationDAO.getOrganizationById(String.valueOf(requester.getRequesterId()));
				break;
			}
		}

		PatientService patientService = new PatientService(patient);

		List<SampleItem> sampleItemList = sampleItemDAO.getSampleItemsBySampleId(order.getId());

		for(SampleItem sampleItem : sampleItemList){
			getResultsForSampleItem(requesterOrganization, patientService, sampleItem, order);
		}
	}

	private void getResultsForSampleItem(Organization requesterOrganization, PatientService patientService, SampleItem sampleItem, Sample order){
		List<Analysis> analysisList = analysisDAO.getAnalysesBySampleItem(sampleItem);

		for(Analysis analysis : analysisList){
			getResultForAnalysis(requesterOrganization, patientService, order, sampleItem, analysis);
		}

	}

	private void getResultForAnalysis(Organization requesterOrganization, PatientService patientService, Sample order, SampleItem sampleItem,
			Analysis analysis){
		TestSegmentedExportBean ts = new TestSegmentedExportBean();

		ts.setAccessionNumber(order.getAccessionNumber());
		ts.setReceptionDate(order.getEnteredDateForDisplay());
		ts.setAge(createReadableAge(patientService.getDOB()));
		ts.setDOB(patientService.getDOB());
		ts.setFirstName(patientService.getFirstName());
		ts.setLastName(patientService.getLastName());
		ts.setGender(patientService.getGender());
		ts.setNationalId(patientService.getNationalId());
		ts.setStatus(StatusService.getInstance().getStatusName(StatusService.getInstance().getAnalysisStatusForID(analysis.getStatusId())));
		ts.setSampleType(sampleItem.getTypeOfSample().getLocalizedName());
		ts.setTestBench(analysis.getTestSection() == null ? "" : analysis.getTestSection().getTestSectionName());
		ts.setTestName(analysis.getTest() == null ? "" : analysis.getTest().getTestName());
		
		if(requesterOrganization != null){
			ts.setSiteCode(requesterOrganization.getShortName());
			ts.setReferringSiteName(requesterOrganization.getOrganizationName());
		}
		
		if(StatusService.getInstance().getStatusID(AnalysisStatus.Finalized).equals(analysis.getStatusId())){
			ts.setResultDate(DateUtil.convertSqlDateToStringDate(analysis.getCompletedDate()));

			List<Result> resultList = resultDAO.getResultsByAnalysis(analysis);
			if(!resultList.isEmpty()){
				setAppropriateResults(resultList, ts);
			}
		}
		testExportList.add(ts);
	}

	@Override
	protected String errorReportFileName(){
		return HAITI_ERROR_REPORT;
	}

	@Override
	protected String reportFileName(){
		return "haitiLNSPExport";
	}

	/**
	 * @see us.mn.state.health.lims.reports.action.implementation.Report#getContentType()
	 */
	@Override
	public String getContentType(){
		if(errorFound){
			return super.getContentType();
		}else{
			return "application/pdf; charset=UTF-8";
		}
	}

	@Override
	public byte[] runReport() throws Exception{
		StringBuilder builder = new StringBuilder();
		builder.append(TestSegmentedExportBean.getHeader());
		builder.append("\n");

		for(TestSegmentedExportBean testLine : testExportList){
			builder.append(testLine.getAsCSVString());
			builder.append("\n");
		}

		return builder.toString().getBytes();
	}

	@Override
	public String getResponseHeaderName(){
		return "Content-Disposition";
	}

	@Override
	public String getResponseHeaderContent(){
		return "attachment;filename=" + getReportFileName() + ".csv";
	}

	/**
	 * check everything
	 */
	private boolean validateSubmitParameters(){
		return dateRange.validateHighLowDate("report.error.message.date.received.missing");
	}

	private String createReadableAge(String dob){
		System.out.println(dob);
		if(GenericValidator.isBlankOrNull(dob)){
			return "";
		}

		dob = dob.replaceAll("xx", "01");
		Date dobDate = DateUtil.convertStringDateToSqlDate(dob);
		int months = DateUtil.getAgeInMonths(dobDate, DateUtil.getNowAsSqlDate());
		if(months > 35){
			return ((int)months / 12) + " A";
		}else if(months > 0){
			return months + " M";
		}else{
			int days = DateUtil.getAgeInDays(dobDate, DateUtil.getNowAsSqlDate());
			return days + " J";
		}

	}

	private void setAppropriateResults(List<Result> resultList, TestSegmentedExportBean data){
		Result result = resultList.get(0);
		String reportResult = "";
		if("D".equals(result.getResultType())){
			Dictionary dictionary = new Dictionary();
			for(Result siblingResult : resultList){
				if(!GenericValidator.isBlankOrNull(siblingResult.getValue()) && !"null".endsWith(siblingResult.getValue())){
					dictionary.setId(siblingResult.getValue());
					dictionaryDAO.getData(dictionary);
					reportResult = dictionary.getId() != null ? dictionary.getLocalizedName() : "";
					if(siblingResult.getAnalyte() != null && "Conclusion".equals(siblingResult.getAnalyte().getAnalyteName())){
						break;
					}
				}
			}
		}else if("Q".equals(result.getResultType())){
			List<Result> childResults = resultDAO.getChildResults(result.getId());
			String childResult = childResults.get(0).getValue();

			Dictionary dictionary = new Dictionary();
			dictionary.setId(result.getValue());
			dictionaryDAO.getData(dictionary);

			reportResult = (dictionary.getId() != null ? dictionary.getLocalizedName() : "") + ": " + ("".equals(childResult) ? "n/a" : childResult);

		}else if("M".equals(result.getResultType())){
			Dictionary dictionary = new Dictionary();
			StringBuilder multiResult = new StringBuilder();

			Collections.sort(resultList, new Comparator<Result>(){
				@Override
				public int compare(Result o1, Result o2){
					return Integer.parseInt(o1.getSortOrder()) - Integer.parseInt(o2.getSortOrder());
				}
			});

			for(Result subResult : resultList){
				dictionary.setId(subResult.getValue());
				dictionaryDAO.getData(dictionary);

				if(dictionary.getId() != null){
					multiResult.append(dictionary.getLocalizedName());
					multiResult.append(", ");
				}
			}

			if(multiResult.length() > 1){
				// remove last ","
				multiResult.setLength(multiResult.length() - 2);
			}

			reportResult = multiResult.toString();
		}else{
			reportResult = result.getValue();
		}

		data.setResult(reportResult.replace(",", ";"));

	}

}