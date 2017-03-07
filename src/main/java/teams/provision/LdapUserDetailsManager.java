package teams.provision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapOperations;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.support.LdapEncoder;
import teams.domain.Member;
import teams.domain.Person;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class LdapUserDetailsManager implements UserDetailsManager {

  private static final Logger LOG = LoggerFactory.getLogger(LdapUserDetailsManager.class);

  private final LdapOperations ldapOperations;

  public LdapUserDetailsManager(LdapOperations ldapOperations) {
    this.ldapOperations = ldapOperations;
  }

  @Override
  public boolean existingPerson(String urn) {
    List<String> personIds = persons(urn, attributes -> this.safeGetAttribute(attributes,"collabpersonid"));
    return !personIds.isEmpty();
  }

  private <T> List<T> persons(String urn, AttributesMapper<T> attributesMapper) {
    AndFilter filter = new AndFilter()
      .and(new EqualsFilter("objectclass", "collabPerson"))
      .and(new EqualsFilter("collabpersonid", urn));

    String encode = filter.encode();

    LOG.info("LDAP query {}", encode);

    //we have provided the ldapOperations with a base so here we need an empty String
    List<T> results = ldapOperations.search("", encode,
      attributesMapper);

    LOG.info("LDAP query result {}", results);
    return results;
  }

  @Override
  public void createPerson(Person person) {
    String organization = person.getSchacHomeOrganization();
    if (!existingOrganisation(organization)) {
      createOrganisation(organization);
    }

    Attributes userAttributes = new BasicAttributes();
    userAttributes.put("collabpersonid", person.getId());
    userAttributes.put("cn", person.getDisplayName());
    userAttributes.put("sn", person.getDisplayName());
    userAttributes.put("mail", person.getEmail());
    userAttributes.put("uid", person.getName());
    userAttributes.put("o", organization);

    BasicAttribute classAttribute = new BasicAttribute("objectclass");
    classAttribute.add("collabPerson");
    classAttribute.add("inetOrgPerson");
    classAttribute.add("person");
    classAttribute.add("top");

    userAttributes.put(classAttribute);

    String dn = String.format("uid=%s,o=%s", LdapEncoder.nameEncode(person.getName()), LdapEncoder.nameEncode(organization));

    LOG.info("LDAP bind {} for {}", userAttributes, dn);

    ldapOperations.bind(dn, null, userAttributes);
  }

  @Override
  public Optional<teams.migration.Person> findPersonById(String urn) {
    List<teams.migration.Person> persons = persons(urn, attributes -> {
      String mail = this.safeGetAttribute(attributes, "mail");
      String cn = this.safeGetAttribute(attributes, "cn");
      String isGuest = this.safeGetAttribute(attributes, "collabpersonisguest");
      return new teams.migration.Person(urn, cn, mail, "TRUE".equals(isGuest));
    });
    return persons.stream().findFirst();
  }

  protected boolean existingOrganisation(String organization) {
    AndFilter filter = new AndFilter()
      .and(new EqualsFilter("objectclass", "organization"))
      .and(new EqualsFilter("objectclass", "top"))
      .and(new EqualsFilter("o", organization));

    String encode = filter.encode();

    LOG.info("LDAP query {}", encode);

    //we have provided the ldapOperations with a base so here we need an empty String
    List<String> organisations = ldapOperations.search("", encode,
      (AttributesMapper<String>) attributes -> (String) attributes.get("o").get());

    return !organisations.isEmpty();
  }

  private void createOrganisation(String organization) {
    Attributes organisationAttributes = new BasicAttributes();
    organisationAttributes.put("o", organization);

    BasicAttribute classAttribute = new BasicAttribute("objectclass");
    classAttribute.add("organization");
    classAttribute.add("top");

    organisationAttributes.put(classAttribute);

    String dn = String.format("o=%s", organization);

    LOG.info("LDAP bind {} for {}", organisationAttributes, dn);

    ldapOperations.bind(dn, null, organisationAttributes);
  }

  private String safeGetAttribute(Attributes attributes, String name) throws NamingException {
    Attribute attribute = attributes.get(name);
    return attribute == null ? null : (String) attribute.get();
  }

}