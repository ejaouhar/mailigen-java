package com.mg.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONObject;

public class MGAPI {
	private static final int SOFT_BOUNCES = 0;
	private static final int HARD_BOUNCES = 1;
	private static final int UNSUBSCRIBES = 2;
	private static final int BLOCKED_BOUNCES = 3;
	private static final int TEMPORARY_BOUNCES = 4;
	private static final int GENERIC_BOUNCES = 5;
	
	private String version = "1.4";
	private String errorMessage;
	private String errorCode;
	private URI apiUrl;
	private int timeout = 300;

	private int chunkSize = 8192;
	private String api_key;
	private boolean secure = false;

	public MGAPI(String apikey, boolean secure) throws MGAPIException {
		this.secure = secure;
		try {
			this.apiUrl = new URI(new StringBuilder().append("http://api.mailigen.com/").append(this.version).append("/?output=json").toString());
		} catch (URISyntaxException e) {
			throw new MGAPIException("Uri syntax error.", e);
		}

		this.api_key = apikey;
	}

	public boolean setTimeout(int seconds) {
		this.timeout = seconds;
		return true;
	}

	public int getTimeout() {
		return this.timeout;
	}

	public String getVersion() {
		return this.version;
	}

	public boolean setSecure(boolean secure) {
		this.secure = secure;
		return true;
	}

	public boolean getSecure() {
		return this.secure;
	}

	/** @deprecated */
	public void useSecure(boolean val) {
		if (val == true)
			this.secure = true;
		else
			this.secure = false;
	}

	private JSONObject callServer(String method, TreeMap<String, String> requestParams) throws MGAPIException {
		String host = this.apiUrl.getHost();
		String path = this.apiUrl.getPath();
		String query = this.apiUrl.getQuery();

		requestParams.put("apikey", this.api_key);

		this.errorMessage = "";
		this.errorCode = "";
		String serverResponse = "";

		String postVars = buildHttpQuery(requestParams);
		JSONObject result = null;
		try {
			URI address = new URI(new StringBuilder().append(this.secure ? "https" : "http").append("://").append(host).append(path).append("?").append(query).append("&method=").append(method)
					.toString());

			URL url = address.toURL();

			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-type", "application/x-www-form-urlencoded");

			conn.setRequestProperty("User-Agent", new StringBuilder().append("MGAPI/").append(getVersion()).append(" JAVA").toString());

			conn.setConnectTimeout(this.timeout * 1000);
			conn.setRequestProperty("Connection", "close");

			conn.setDoOutput(true);
			OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream(), "UTF-8");

			wr.write(postVars);
			wr.flush();

			BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
			String line;
			while ((line = rd.readLine()) != null) {
				serverResponse = new StringBuilder().append(serverResponse).append(line).toString();
			}
			wr.close();
			rd.close();
			try {
				result = new JSONObject(serverResponse);
				if (result.has("error"))
					throw new MGAPIException(result.getString("error"));
			} catch (ParseException e) {
				result = new JSONObject(new StringBuilder().append("{reply: ").append(serverResponse).append("}").toString());
			}
		} catch (URISyntaxException e) {
			throw new MGAPIException(e.getMessage(), e);
		} catch (MalformedURLException e) {
			throw new MGAPIException(e.getMessage(), e);
		} catch (IOException e) {
			throw new MGAPIException(e.getMessage(), e);
		} catch (ParseException e) {
			try {
				result = new JSONObject(new StringBuilder().append("{error: 'Send/receive data error'}").append(e.getMessage()).toString());
			} catch (ParseException ex) {
				throw new MGAPIException("ParseException (JSON parse error)", ex);
			}

		}

		return result;
	}

	private String buildHttpQuery(TreeMap<String, String> requestParams) throws MGAPIException {
		String result = "";

		for (Map.Entry<String,String> param : requestParams.entrySet()) {
			try {
				result = new StringBuilder().append(result).append(URLEncoder.encode((String) param.getKey(), "UTF-8")).append("=").append(URLEncoder.encode((String) param.getValue(), "UTF-8"))
						.append("&").toString();
			} catch (UnsupportedEncodingException e) {
				throw new MGAPIException("UnsopportedEncodingException", e);
			}
		}

		return result;
	}

	private void addToRequestParams(TreeMap<String, String> requestParams, String keyName, TreeMap<String, String> keyValuePairs) {
		for (Map.Entry<String,String> param : keyValuePairs.entrySet())
			requestParams.put(new StringBuilder().append(keyName).append("[").append((String) param.getKey()).append("]").toString(), param.getValue());
	}

	private void addToRequestParams(TreeMap<String, String> requestParams, String keyName, String[] values) {
		for (int i = 0; i < values.length; i++)
			requestParams.put(new StringBuilder().append(keyName).append("[").append(Integer.toString(i)).append("]").toString(), values[i]);
	}

	private void addToRequestParams(TreeMap<String, String> requestParams, String keyName, ArrayList<TreeMap> arrOfMaps) {
		Iterator arrOfMapsItr = arrOfMaps.iterator();
		int i = 0;
		while (arrOfMapsItr.hasNext()) {
			TreeMap tm = (TreeMap) arrOfMapsItr.next();

			Iterator mapItr = tm.entrySet().iterator();
			while (mapItr.hasNext()) {
				Map.Entry pairs = (Map.Entry) mapItr.next();

				requestParams.put(new StringBuilder().append(keyName).append("[").append(i).append("][").append(pairs.getKey().toString()).append("]").toString(), pairs.getValue().toString());
			}

			i++;
		}
	}

	public ArrayList campaigns(TreeMap filters, int start, int limit) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("start", Integer.toString(start));
		requestParams.put("limit", Integer.toString(limit));
		ArrayList campaignList = new ArrayList();

		addToRequestParams(requestParams, "filters", filters);
		try {
			JSONObject result = callServer("campaigns", requestParams);

			if ((result == null) || (!result.has("reply"))) {
				throw new MGAPIException("campaigns: server reply is null");
			}

			JSONArray campaignJSONArray = result.getJSONArray("reply");
			for (int i = 0; i < campaignJSONArray.length(); i++) {
				JSONObject campaignJSONObject = campaignJSONArray.getJSONObject(i);
				campaignList.add(campaignJSONObject);
			}
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return campaignList;
	}

	public boolean campaignUnschedule(String cid) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("cid", cid);
		try {
			JSONObject result = callServer("campaignUnschedule", requestParams);
		} catch (MGAPIException e) {
			
			throw new MGAPIException(e.getMessage(), e);
		}

		return true;
	}

	public boolean campaignSchedule(String cid, String scheduleTime) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("cid", cid);
		requestParams.put("schedule_time", scheduleTime);
		boolean retval = false;
		try {
			JSONObject result = callServer("campaignSchedule", requestParams);
			if (!result.has("error"))
				retval = true;
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public boolean campaignResume(String cid) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("cid", cid);
		boolean retval = false;
		try {
			JSONObject result = callServer("campaignResume", requestParams);
			if (!result.has("error"))
				retval = true;
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public boolean campaignPause(String cid) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("cid", cid);
		boolean retval = false;
		try {
			JSONObject result = callServer("campaignPause", requestParams);
			if (!result.has("error"))
				retval = true;
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public boolean campaignSendNow(String cid) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("cid", cid);

		boolean retval = false;
		try {
			JSONObject result = callServer("campaignSendNow", requestParams);
			if (!result.has("error"))
				retval = true;
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public boolean campaignSendTest(String cid, String[] test_emails, String send_type) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("cid", cid);
		requestParams.put("send_type", send_type);

		addToRequestParams(requestParams, "test_emails", test_emails);

		boolean retval = false;
		try {
			JSONObject result = callServer("campaignSendTest", requestParams);
			if (!result.has("error"))
				retval = true;
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public ArrayList campaignTemplates() throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		ArrayList templatesList = new ArrayList();
		try {
			JSONObject result = callServer("campaignTemplates", requestParams);

			if ((result == null) || (!result.has("reply"))) {
				throw new MGAPIException("campaignTemplates error: server reply is null");
			}

			JSONArray templatesJSONArray = result.getJSONArray("reply");
			for (int i = 0; i < templatesJSONArray.length(); i++) {
				JSONObject templateJSONObject = templatesJSONArray.getJSONObject(i);
				templatesList.add(templateJSONObject);
			}
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return templatesList;
	}

	public String campaignCreate(String type, TreeMap<String, String> options, TreeMap<String, String> tracking, TreeMap<String, String> analytics, TreeMap<String, String> content,
			TreeMap<String, String> type_opts) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("type", type);

		addToRequestParams(requestParams, "options", options);
		addToRequestParams(requestParams, "content", content);
		addToRequestParams(requestParams, "tracking", tracking);
		addToRequestParams(requestParams, "analytics", analytics);
		addToRequestParams(requestParams, "type_opts", type_opts);

		String retval = "";
		try {
			JSONObject result = callServer("campaignCreate", requestParams);
			if (!result.has("reply")) {
				throw new MGAPIException("Unexpected reply from server");
			}

			retval = result.getString("reply");
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public String campaignReplicate(String cid) throws MGAPIException {
		TreeMap requestParams = new TreeMap();

		requestParams.put("cid", cid);
		String retval = "";
		try {
			JSONObject result = callServer("campaignReplicate", requestParams);
			if (result.has("reply"))
				retval = result.getString("reply");
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public boolean campaignUpdate(String cid, String name, String value) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("cid", cid);
		requestParams.put("name", name);
		requestParams.put("value", value);

		boolean retval = false;
		try {
			JSONObject result = callServer("campaignUpdate", requestParams);
			if ((!result.has("error")) && (result.has("reply"))) {
				retval = true;
			}
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public boolean campaignDelete(String cid) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("cid", cid);
		boolean retval = false;
		try {
			JSONObject result = callServer("campaignDelete", requestParams);
			if (result.has("reply"))
				retval = result.getBoolean("reply");
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public JSONObject campaignStats(String cid) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("cid", cid);

		JSONObject result = null;
		try {
			result = callServer("campaignStats", requestParams);
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return result;
	}

	public JSONObject campaignClickStats(String cid) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("cid", cid);
		JSONObject stats = null;
		try {
			JSONObject result = callServer("campaignClickStats", requestParams);

			if (result.has("reply")) {
				JSONArray reply = result.getJSONArray("reply");
				if (!reply.isNull(0)) {
					stats = reply.getJSONObject(0);
				} else
					stats = new JSONObject();
			}
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return stats;
	}

	public ArrayList campaignEmailDomainPerformance(String cid) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("cid", cid);
		ArrayList domainStatsArr = new ArrayList();
		try {
			JSONObject result = callServer("campaignEmailDomainPerformance", requestParams);

			if (result.has("reply")) {
				JSONArray arr = result.getJSONArray("reply");
				for (int i = 0; i < arr.length(); i++)
					domainStatsArr.add(arr.get(i));
			}
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return domainStatsArr;
	}

	private ArrayList getCampaignEmailsByType(int type, String cid, int start, int limit) throws MGAPIException {
		ArrayList emails = new ArrayList();
		TreeMap requestParams = new TreeMap();
		requestParams.put("cid", cid);
		requestParams.put("start", Integer.toString(start));
		requestParams.put("limit", Integer.toString(limit));
		String serverCallMethod = "";

		switch (type) {
		case SOFT_BOUNCES:
			serverCallMethod = "campaignSoftBounces";
			break;
		case HARD_BOUNCES:
			serverCallMethod = "campaignHardBounces";
			break;
		case BLOCKED_BOUNCES:
			serverCallMethod = "campaignBlockedBounces";
			break;
		case TEMPORARY_BOUNCES:
			serverCallMethod = "campaignTemporaryBounces";
			break;
		case GENERIC_BOUNCES:
			serverCallMethod = "campaignGenericBounces";
			break;
		case UNSUBSCRIBES:
			serverCallMethod = "campaignUnsubscribes";
			break;
		default:
			throw new MGAPIException("Incorrect type provided");
		}

		try {
			JSONObject result = callServer(serverCallMethod, requestParams);
			if (result.has("reply")) {
				JSONArray arr = result.getJSONArray("reply");
				for (int i = 0; i < arr.length(); i++)
					emails.add(arr.get(i));
			}
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return emails;
	}

	public ArrayList campaignHardBounces(String cid, int start, int limit) throws MGAPIException {
		ArrayList emails = getCampaignEmailsByType(HARD_BOUNCES, cid, start, limit);

		return emails;
	}

	public ArrayList campaignSoftBounces(String cid, int start, int limit) throws MGAPIException {
		ArrayList emails = getCampaignEmailsByType(SOFT_BOUNCES, cid, start, limit);

		return emails;
	}

	public ArrayList campaignBlockedBounces(String cid, int start, int limit) throws MGAPIException {
		ArrayList emails = getCampaignEmailsByType(BLOCKED_BOUNCES, cid, start, limit);

		return emails;
	}

	public ArrayList campaignTemporaryBounces(String cid, int start, int limit) throws MGAPIException {
		ArrayList emails = getCampaignEmailsByType(TEMPORARY_BOUNCES, cid, start, limit);

		return emails;
	}

	public ArrayList campaignGenericBounces(String cid, int start, int limit) throws MGAPIException {
		ArrayList emails = getCampaignEmailsByType(GENERIC_BOUNCES, cid, start, limit);

		return emails;
	}

	public ArrayList campaignUnsubscribes(String cid, int start, int limit) throws MGAPIException {
		ArrayList emails = getCampaignEmailsByType(UNSUBSCRIBES, cid, start, limit);

		return emails;
	}

	public ArrayList campaignGeoOpens(String cid) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("cid", cid);
		ArrayList stats = new ArrayList();
		try {
			JSONObject result = callServer("campaignGeoOpens", requestParams);

			if (result.has("reply")) {
				JSONArray arr = result.getJSONArray("reply");
				for (int i = 0; i < arr.length(); i++)
					stats.add(arr.get(i));
			}
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return stats;
	}

	public JSONObject campaignGeoOpensByCountry(String cid, String code) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("cid", cid);
		requestParams.put("code", code);
		JSONObject stat = null;
		try {
			stat = callServer("campaignGeoOpensByCountry", requestParams);
			if (stat.has("reply"))
				stat = null;
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return stat;
	}

	public ArrayList campaignForwardStats(String cid, int start, int limit) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("cid", cid);
		requestParams.put("start", Integer.toString(start));
		requestParams.put("limit", Integer.toString(limit));
		ArrayList stats = new ArrayList();
		try {
			JSONObject result = callServer("campaignForwardStats", requestParams);

			if (result.has("reply")) {
				JSONArray arr = result.getJSONArray("reply");
				for (int i = 0; i < arr.length(); i++)
					stats.add(arr.get(i));
			}
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return stats;
	}

	public ArrayList campaignBounceMessages(String cid, int start, int limit) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("cid", cid);
		requestParams.put("start", Integer.toString(start));
		requestParams.put("limit", Integer.toString(limit));
		ArrayList messages = new ArrayList();
		try {
			JSONObject result = callServer("campaignBounceMessages", requestParams);

			if (result.has("reply")) {
				JSONArray arr = result.getJSONArray("reply");
				for (int i = 0; i < arr.length(); i++)
					messages.add(arr.get(i));
			}
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return messages;
	}

	public String listCreate(String title, TreeMap<String, String> options) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("title", title);

		addToRequestParams(requestParams, "options", options);

		String retval = "";
		try {
			JSONObject result = callServer("listCreate", requestParams);
			if (!result.has("reply")) {
				throw new MGAPIException("Unexpected reply from server");
			}

			retval = result.getString("reply");
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public boolean listUpdate(String id, String name, String value) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("id", id);
		requestParams.put("name", name);
		requestParams.put("value", value);

		boolean retval = false;
		try {
			JSONObject result = callServer("listUpdate", requestParams);
			if ((!result.has("error")) && (result.has("reply"))) {
				retval = true;
			}
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public boolean listDelete(String id) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("id", id);
		boolean retval = false;
		try {
			JSONObject result = callServer("listDelete", requestParams);
			if (result.has("reply"))
				retval = result.getBoolean("reply");
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public ArrayList lists(int start, int limit) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("start", Integer.toString(start));
		requestParams.put("limit", Integer.toString(limit));
		ArrayList lists = new ArrayList();
		try {
			JSONObject result = callServer("lists", requestParams);
			if (result.has("reply")) {
				JSONArray arr = result.getJSONArray("reply");
				for (int i = 0; i < arr.length(); i++)
					lists.add(arr.get(i));
			}
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return lists;
	}

	public ArrayList listMergeVars(String lid) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("id", lid);
		ArrayList mergeVars = new ArrayList();
		try {
			JSONObject result = callServer("listMergeVars", requestParams);
			if (result.has("reply")) {
				JSONArray arr = result.getJSONArray("reply");
				for (int i = 0; i < arr.length(); i++)
					mergeVars.add(arr.get(i));
			}
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return mergeVars;
	}

	public boolean listMergeVarAdd(String lid, String tag, String name, TreeMap options) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("id", lid);
		requestParams.put("tag", tag);
		requestParams.put("name", name);
		boolean retval = false;

		addToRequestParams(requestParams, "options", options);
		try {
			JSONObject result = callServer("listMergeVarAdd", requestParams);
			if (result.getBoolean("reply"))
				retval = true;
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public boolean listMergeVarUpdate(String lid, String tag, TreeMap options) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("id", lid);
		requestParams.put("tag", tag);
		boolean retval = false;

		addToRequestParams(requestParams, "options", options);
		try {
			JSONObject result = callServer("listMergeVarUpdate", requestParams);
			if (result.getBoolean("reply"))
				retval = true;
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public boolean listMergeVarDel(String lid, String tag) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("id", lid);
		requestParams.put("tag", tag);
		boolean retval = false;
		try {
			JSONObject result = callServer("listMergeVarDel", requestParams);
			if (result.getBoolean("reply"))
				retval = true;
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public boolean listSubscribe(String lid, String email_address, TreeMap merge_vars, String email_type, boolean double_optin, boolean update_existing, boolean send_welcome) throws MGAPIException {
		boolean retval = false;

		TreeMap requestParams = new TreeMap();
		requestParams.put("id", lid);
		requestParams.put("email_address", email_address);
		requestParams.put("email_type", email_type);
		requestParams.put("double_optin", Boolean.toString(double_optin));
		requestParams.put("update_existing", Boolean.toString(update_existing));
		requestParams.put("send_welcome", Boolean.toString(send_welcome));

		addToRequestParams(requestParams, "merge_vars", merge_vars);
		try {
			JSONObject result = callServer("listSubscribe", requestParams);
			if (result.has("reply"))
				retval = result.getBoolean("reply");
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public boolean listUnsubscribe(String lid, String email_address, boolean delete_member, boolean send_goodbye, boolean send_notify) throws MGAPIException {
		boolean retval = false;
		TreeMap requestParams = new TreeMap();
		requestParams.put("id", lid);
		requestParams.put("email_address", email_address);
		requestParams.put("delete_member", Boolean.toString(delete_member));
		requestParams.put("send_goodbye", Boolean.toString(send_goodbye));
		requestParams.put("send_notify", Boolean.toString(send_notify));
		try {
			JSONObject result = callServer("listUnsubscribe", requestParams);
			if (result.has("reply"))
				retval = result.getBoolean("reply");
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public boolean listUpdateMember(String lid, String email_address, TreeMap merge_vars, String email_type) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("id", lid);
		requestParams.put("email_address", email_address);
		requestParams.put("email_type", email_type);

		addToRequestParams(requestParams, "merge_vars", merge_vars);
		boolean retval = false;
		try {
			JSONObject result = callServer("listUpdateMember", requestParams);
			if (result.has("reply"))
				retval = result.getBoolean("reply");
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public JSONObject listBatchSubscribe(String lid, ArrayList<TreeMap> batch, boolean double_optin, boolean update_existing) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("id", lid);
		requestParams.put("double_optin", Boolean.toString(double_optin));
		requestParams.put("update_existing", Boolean.toString(update_existing));
		JSONObject result = null;

		addToRequestParams(requestParams, "batch", batch);
		try {
			result = callServer("listBatchSubscribe", requestParams);
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return result;
	}

	public JSONObject listBatchUnsubscribe(String lid, String[] emails, boolean delete_member, boolean send_goodbye, boolean send_notify) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("id", lid);
		requestParams.put("delete_member", Boolean.toString(delete_member));
		requestParams.put("send_goodbye", Boolean.toString(send_goodbye));
		requestParams.put("send_notify", Boolean.toString(send_notify));

		addToRequestParams(requestParams, "emails", emails);

		JSONObject result = null;
		try {
			result = callServer("listBatchUnsubscribe", requestParams);
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return result;
	}

	public JSONArray listMembers(String lid, String status, int start, int limit) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("id", lid);
		requestParams.put("status", status);
		requestParams.put("start", Integer.toString(start));
		requestParams.put("limit", Integer.toString(limit));

		JSONArray retArray = null;
		try {
			JSONObject result = callServer("listMembers", requestParams);
			if (result.has("reply"))
				retArray = result.getJSONArray("reply");
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retArray;
	}

	public JSONObject listMemberInfo(String lid, String email_address) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("id", lid);
		requestParams.put("email_address", email_address);

		JSONObject result = null;
		try {
			result = callServer("listMemberInfo", requestParams);
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return result;
	}

	public JSONArray listGrowthHistory(String lid) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("id", lid);
		JSONArray histories = null;
		try {
			JSONObject result = callServer("listGrowthHistory", requestParams);
			histories = result.getJSONArray("reply");
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return histories;
	}

	public ArrayList listSegments(String lid) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("id", lid);
		ArrayList lists = new ArrayList();
		try {
			JSONObject result = callServer("listSegments", requestParams);
			if (result.has("reply")) {
				JSONArray arr = result.getJSONArray("reply");
				for (int i = 0; i < arr.length(); i++)
					lists.add(arr.get(i));
			}
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return lists;
	}

	public String listSegmentCreate(String list, String title, String match, ArrayList<TreeMap> filter) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("list", list);
		requestParams.put("title", title);
		requestParams.put("match", match);

		addToRequestParams(requestParams, "filter", filter);

		String retval = "";
		try {
			JSONObject result = callServer("listSegmentCreate", requestParams);
			if (!result.has("reply")) {
				throw new MGAPIException("Unexpected reply from server");
			}

			retval = result.getString("reply");
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public boolean listSegmentUpdate(String sid, String name, String value) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("sid", sid);
		requestParams.put("name", name);
		requestParams.put("value", value);

		boolean retval = false;
		try {
			JSONObject result = callServer("listSegmentUpdate", requestParams);
			if ((!result.has("error")) && (result.has("reply"))) {
				retval = true;
			}
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public boolean listSegmentUpdate(String sid, String name, ArrayList<TreeMap> value) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("sid", sid);
		requestParams.put("name", name);

		addToRequestParams(requestParams, "value", value);

		boolean retval = false;
		try {
			JSONObject result = callServer("listSegmentUpdate", requestParams);
			if ((!result.has("error")) && (result.has("reply"))) {
				retval = true;
			}
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public boolean listSegmentDelete(String sid) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("sid", sid);
		boolean retval = false;
		try {
			JSONObject result = callServer("listSegmentDelete", requestParams);
			if (result.has("reply"))
				retval = result.getBoolean("reply");
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public JSONObject getAccountDetails() throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		JSONObject result = null;
		try {
			result = callServer("getAccountDetails", requestParams);
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return result;
	}

	public JSONArray listsForEmail(String email_address) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("email_address", email_address);
		JSONArray lists = null;
		try {
			JSONObject result = callServer("listsForEmail", requestParams);
			if (result.has("reply"))
				lists = result.getJSONArray("reply");
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return lists;
	}

	public JSONArray apikeys(String username, String password, boolean expired) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("username", username);
		requestParams.put("password", password);
		requestParams.put("expired", Boolean.toString(expired));
		JSONArray keys = null;
		try {
			JSONObject result = callServer("apikeys", requestParams);
			if (result.has("reply"))
				keys = result.getJSONArray("reply");
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return keys;
	}

	public String apikeyAdd(String username, String password) throws MGAPIException {
		TreeMap requestParams = new TreeMap();

		requestParams.put("username", username);
		requestParams.put("password", password);
		String retval = "";
		try {
			JSONObject result = callServer("apiKeyAdd", requestParams);
			if (result.has("reply"))
				retval = result.getString("reply");
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public boolean apikeyExpire(String username, String password) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("username", username);
		requestParams.put("password", password);
		boolean retval = false;
		try {
			JSONObject result = callServer("apikeyExpire", requestParams);
			retval = result.getBoolean("reply");
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public String login(String username, String password) throws MGAPIException {
		TreeMap requestParams = new TreeMap();
		requestParams.put("username", username);
		requestParams.put("password", password);
		String retval = null;
		try {
			JSONObject result = callServer("login", requestParams);
			if (result.has("reply"))
				retval = result.getString("reply");
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return retval;
	}

	public String ping() throws MGAPIException {
		String pingReply = null;
		try {
			JSONObject result = callServer("ping", new TreeMap());
			if (!result.has("reply")) {
				throw new MGAPIException("Unexpected reply from server");
			}

			pingReply = result.getString("reply");
		} catch (MGAPIException e) {
			throw new MGAPIException(e.getMessage(), e);
		}

		return pingReply;
	}
}