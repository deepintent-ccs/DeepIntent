package edu.cwru.android.ui.correlation;



import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UIKeywordFactory {

	// Categories for UI sensitive data. Some may not be that sensitive but at the beginning, using them can get results
	// to judge the capability of the system.
	private final static String Identity = "UiIdentity";
	private final static String Credential = "UiCredential";
	private final static String Contact = "UiContact";
	private final static String Account = "UiAccount";
	private final static String CreditCard = "UiCreditCard";
	private final static String SSN = "UiSSN";
	private final static String Protection = "UiProtection";
	private final static String PersonalInfo = "UiPersonalInfo";
	private final static String Health = "UiHealth";
	private final static String FinancialInfo = "UiFinancialInfo";

	// \b: word boundary, e.g. white space, comma, slash, etc, not including any non-English characters like Chinese and
	// Korean. '#' cannot be matched if the pattern ends with '\b'.
//	private final static Pattern patternIdentity = Pattern
//			.compile("아이디|用户名|\\b(user(\\s?name|\\sid)((\\sor\\s|/)e-?mail)?|nick\\s*name)\\b");
	private final static Pattern patternIdentity = Pattern
			.compile("아이디|用户名|\\b(user(\\s?name|\\sid)((\\sor\\s|/)e-?mail)?|e-?mail(\\sor\\s|/)user(\\s)?name|nick\\s*name|moniker|cognomen|sobriquet|soubriquet|byname)\\b");
//	private final static Pattern patternCredential = Pattern
//			.compile("비밀번호|密(　|\\s)*码|密(　|\\s)*碼|\\b(pin(\\s(code|number|no|#))?|password|security\\spasscode)\\b");
	private final static Pattern patternCredential = Pattern
			.compile("비밀번호|密(　|\\s)*码|密(　|\\s)*碼|\\b(pin(code|\\s(code|number|no|#))?|personal\\sidentification\\s(number|no)|password(s)?|passwort|watchword|parole|countersign|(security\\s)?passcode)\\b");
	private final static Pattern patternContact = Pattern
			.compile("이메일|電子郵件|(电子)?邮(　|\\s)*箱|手机号(码)?|手機號(碼)?|\\b((phone:)?e-?mail|e-?mail(\\s)?address(es)?|(mobile\\s|tele|cell|your\\s)?phone(\\s(no|number|#))?|mobile\\s(no|number|#)|gmail|contact(s|\\sname)|fax|phnnum)\\b");
//	private final static Pattern patternAccount = Pattern
//			.compile("登(　|\\s)*录|登(　|\\s)*入|\\b((your\\s)?login(\\scredential(s)?)?|regist(er|ration)|user\\sauthentication|sign(ing)?\\s(in|up)|log\\s+in(to)?)\\b");
	private final static Pattern patternAccount = Pattern
			.compile("登(　|\\s)*录|登(　|\\s)*入|\\b((your\\s)?login(\\s(credential|certificat(e|ion))(s)?)?|regist(er|ration|ry)|user\\s(authentication|hallmark|assay(\\s|-)mark)|sign(ing)?\\s(in|up)|check\\sin|log(-|\\s+)(in|on)(to)?)\\b");
//	private final static Pattern patternCreditCard = Pattern
//			.compile("银行(卡)?卡号|\\b((credit(　|\\s)?)?card(　|\\s)?(number|no|#)|credit(　|\\s)?card|cvc((　|\\s)+code)?)\\b");;
	private final static Pattern patternCreditCard = Pattern
			.compile("银行(卡)?卡号|\\b(((credit|charge|my|your)(　|\\s)?)?card(　|\\s)?(number|no|#|information|statement|(security|verification)( |\\s)?(number|code|value))|(credit|charge)(　|\\s)?card|cv(c|v)2?((　|\\s)+code)?)\\b");;
	private final static Pattern patternSSN = Pattern
			.compile("身(份|分)證(字)?號|身份證後五碼|身份证(号(码)?)?|\\b(((digits\\s)?of\\s)?ssn|tin|(federal|national)\\s(id|identity)|(your\\s)?social\\ssec(urity)?(\\s(number|no|#))?)\\b");
//	private final static Pattern patternProtection = Pattern
//			.compile("\\bsecurity\\s(answer|code|token)|enter\\syour\\sanswer|identification\\s(code|number|no)|activation\\scode\\b");
	private final static Pattern patternProtection = Pattern
			.compile("\\b(security\\s(answer|code|token|item)|enter\\syour\\s(answer|reply|response)|(identification|designation)\\s(code|number|no)|activation\\s(code|number|no))\\b");
	private final static Pattern patternPersonalInfo = Pattern
			.compile("\\b((first|last)(\\s)?name|age|sex|gender|birth(\\s)?(date|day)?|date\\sof\\birth|interests|dropbox|facebook)\\b");
	private final static Pattern patternHealth = Pattern
			.compile("\\b(weight|height|health|cholesterol|glucose|obese|calories|kcal|doctor|blood(\\stype)?)\\b");
	private final static Pattern patternFinancialInfo = Pattern
			.compile("\\b(repayment|(payment(s)?|deposit|loan)(\\samount)?|income|expir(y|ation)(\\sdate)?|paypal|banking|debit|mortgage|taxable|(down|monthly)\\spayment|payment\\s(information|details)|cardholder's\\sname|billing\\saddress|opening\\sbalance|financial\\sinstitution)\\b");
	
	public static String sensitive(String str) {
		String lowercaseStr = str.toLowerCase();
		//System.out.println(lowercaseStr);
		Matcher matcher = patternCredential.matcher(lowercaseStr);
		if (matcher.find())
			return Credential;
		matcher = patternSSN.matcher(lowercaseStr);
		if (matcher.find())
			return SSN;
		matcher = patternProtection.matcher(lowercaseStr);
		if (matcher.find())
			return Protection;
		matcher = patternCreditCard.matcher(lowercaseStr);
		if (matcher.find())
			return CreditCard;
		matcher = patternFinancialInfo.matcher(lowercaseStr);
		if (matcher.find())
			return FinancialInfo;
		matcher = patternContact.matcher(lowercaseStr);
		if (matcher.find())
			return Contact;
		matcher = patternHealth.matcher(lowercaseStr);
		if (matcher.find())
			return Health;
		matcher = patternIdentity.matcher(lowercaseStr);
		if (matcher.find())
			return Identity;
		matcher = patternPersonalInfo.matcher(lowercaseStr);
		if (matcher.find())
			return PersonalInfo;
		matcher = patternAccount.matcher(lowercaseStr);
		if (matcher.find())
			return Account;
		return null;
		// if (lowercaseStr.contains("password"))// || lowercaseStr.contains("credit card") ||
		// // lowercaseStr.contains("ssn")) {
		// return "UiPassword";
		// else if (lowercaseStr.contains("credit card"))
		// return "UiCreditCard";
		// else if (lowercaseStr.contains("ssn"))
		// return "UiSSN";
		// return null;
	}
}
