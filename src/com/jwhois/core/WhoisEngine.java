package com.jwhois.core;

import java.net.IDN;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class WhoisEngine extends WhoisClient {
	private static final String	DEFAULT_SERVER_DOMAIN	= ".whois-servers.net";
	private static final String	REGEX_SKIP				= "(^[^0-9a-z]+$)";
	private static final String	REGEX_ESCAPE_HTML		= "\\s*<[a-zA-Z]+[^>]*>\\s*([^<>]*)\\s*</[a-zA-Z]+\\s*>\\s*";

	private WhoisMap			whoisMap;
	private boolean				nonIcann;
	private boolean				deepWhois;
	private String				domain;
	private Pattern				pnSkip;
	private boolean				isVaildDom;

	// Constructor
	public WhoisEngine(String domain) {
		this( domain, true );
	}

	public WhoisEngine(String domain, boolean deepWhois) {
		this.isVaildDom = false;
		this.domain = domain.trim().toLowerCase();
		this.domain = IDN.toASCII( this.domain );
		if (!Utility.isValidDom( this.domain ))
			return;
		this.isVaildDom = true;
		this.whoisMap = null;
		this.nonIcann = false;
		this.deepWhois = deepWhois;
		this.pnSkip = Pattern.compile( REGEX_SKIP, Pattern.CASE_INSENSITIVE );
		this.setLineFilter( new LineFilter() {
			@Override
			public String filterLine(String line) {
				line = line.replaceAll( REGEX_ESCAPE_HTML, "$1" );
				return line;
			}

			@Override
			public String filterHtmlLine(String line) {
				line = line.replaceAll( REGEX_ESCAPE_HTML, "$1" );
				return line;
			}

			@Override
			public boolean skipLine(String line) {
				return line.isEmpty() || pnSkip.matcher( line ).find();
			}

			@Override
			public boolean skipHtmlLine(String line) {
				return Utility.isEmpty( line );
			}
		} );
	}

	/**
	 * build the WhoisMap
	 * 
	 * @return the WhoisMap
	 */
	public WhoisMap build() {
		if (!isVaildDom)
			return null;
		
		if (null != whoisMap)
			return whoisMap;

		whoisMap = new WhoisMap();

		// Build array of all possible tld's for that domain
		List<String> tldtest = Utility.buildTLDs( domain );

		// Test in special list first
		String server = "";
		String tld = "";
		for (String t : tldtest) {
			String s = XMLHelper.getSpecialServer( t, nonIcann );
			if (!Utility.isEmpty( s )) {
				server = s;
				tld = t;
				break;
			}
		}

		// Test with default server
		if (Utility.isEmpty( server )) {
			String cname = null;
			for (String t : tldtest) {
				cname = t + DEFAULT_SERVER_DOMAIN;

				if (Utility.isEmpty( Utility.getAddressbyName( cname ) ))
					continue;

				// Check if has special parameters
				if ("com".equals( t ) || "net".equals( t )) {
					cname += "?domain ={domain}";
				}
				else if ("de".equals( t )) {
					cname += "?-T dn,ace {domain}";
				}

				server = cname;
				tld = t;
				break;
			}
		}

		if (Utility.isEmpty( server ) || Utility.isEmpty( tld )) {
			// return an empty map
			return whoisMap;
		}

		// Set the server
		setServer( server );

		String servername = hostname;

		// Set if has LineStart or LineEnd pattern
		setLineStartFilter( XMLHelper.getTranslateAttr( "LineStart", servername ) );
		setLineEndFilter( XMLHelper.getTranslateAttr( "LineEnd", servername ) );
		setLineCatchFilter( XMLHelper.getTranslateAttr( "LineCatch", servername ) );

		// Get the raw data
		List<String> rawdata = domLookup( domain, tld );
		if (Utility.isEmpty( rawdata )) {
			return whoisMap;
		}

		whoisMap.set( "rawdata", rawdata );

		// Set the necessary fields
		whoisMap.set( "regyinfo.type", "domain" );
		whoisMap.set( "regyinfo.domain", domain );

		List<String> serverList = new ArrayList<String>();
		whoisMap.set( "regyinfo.servers", serverList );

		serverList.add( servername );

		// Parse the map 1st.
		String deepServer = "";
		whoisMap.parse( servername );
		deepServer = whoisMap.deepServer();

		boolean hasWhoisRecord = (null == whoisMap.get( "regyinfo.hasrecord" ))
				? false
				: ( Boolean ) whoisMap.get( "regyinfo.hasrecord" );

		// If set deepWhois, do deep whois query.
		while (deepWhois && !Utility.isEmpty( deepServer )) {
			setServer( deepServer );

			if (hostname.equals( servername ))
				break;

			servername = hostname;
			setLineStartFilter( XMLHelper.getTranslateAttr( "LineStart", servername ) );
			setLineEndFilter( XMLHelper.getTranslateAttr( "LineEnd", servername ) );
			setLineCatchFilter( XMLHelper.getTranslateAttr( "LineCatch", servername ) );

			rawdata = domLookup( domain, tld );
			if (Utility.isEmpty( rawdata ))
				break;

			whoisMap.remove( "regrinfo.domain" );
			whoisMap.remove( "regyinfo.whois" );
			whoisMap.remove( "regyinfo.registrar" );
			whoisMap.set( "rawdata", rawdata );

			serverList.add( servername );

			whoisMap.parse( servername );
			deepServer = whoisMap.deepServer();

			if (!hasWhoisRecord) {
				hasWhoisRecord = (null == whoisMap.get( "regyinfo.hasrecord" ))
						? false
						: ( Boolean ) whoisMap.get( "regyinfo.hasrecord" );
			}
		}

		// Fixed
		if (hasWhoisRecord) {
			whoisMap.set( "regyinfo.hasrecord", true );
		}

		return whoisMap;
	}

	// Options
	/**
	 * If set this flag true, that means this query supports the tld's of Non ICANN. Default value is false.
	 * 
	 * @param flag
	 */
	public void setNonIcann(boolean flag) {
		nonIcann = flag;
	}

	/**
	 * If set this flag true, that means this query will retrieve more details of whois information.
	 * 
	 * @param flag
	 */
	public void setDeepWhois(boolean flag) {
		deepWhois = flag;
	}

}