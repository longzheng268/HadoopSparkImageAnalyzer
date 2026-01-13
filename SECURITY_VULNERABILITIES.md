# Security Vulnerabilities - CRITICAL ADVISORY

## 🚨 CRITICAL: UPGRADE REQUIRED

**CURRENT STATUS: HIGH SECURITY RISK**

The dependencies used in this project contain **CRITICAL VULNERABILITIES** that should NOT be used in production without immediate action.

## ⚠️ STOP - READ THIS FIRST

**DO NOT DEPLOY TO PRODUCTION** with the current vulnerable versions (Hadoop 3.2.0, Spark 3.1.2) without:
1. Upgrading the VM environment to patched versions, OR
2. Implementing ALL security mitigations listed below AND accepting the documented risks

## 🎯 RECOMMENDED ACTION: UPGRADE VM ENVIRONMENT

The **ONLY proper solution** is to upgrade your VM environment:

### Required Upgrades
```bash
# Upgrade Hadoop
Current: 3.2.0  →  Required: 3.2.4 or higher
Fixes: Argument Injection, Heap Overflow, Path Traversal, Privilege Management

# Upgrade Spark  
Current: 3.1.2  →  Required: 3.3.3 or higher
Fixes: Privilege Management vulnerabilities

# Then update build.gradle:
sparkVersion = '3.3.3'
hadoopVersion = '3.2.4'
```

### How to Upgrade VM Environment
```bash
# 1. Backup current installation
sudo tar -czf /backup/hadoop-3.2.0.tar.gz /opt/hadoop
sudo tar -czf /backup/spark-3.1.2.tar.gz /opt/spark

# 2. Download patched versions
wget https://archive.apache.org/dist/hadoop/core/hadoop-3.2.4/hadoop-3.2.4.tar.gz
wget https://archive.apache.org/dist/spark/spark-3.3.3/spark-3.3.3-bin-hadoop3.tgz

# 3. Install and configure
# Follow Apache Hadoop/Spark installation guides

# 4. Verify installation
hadoop version  # Should show 3.2.4
spark-submit --version  # Should show 3.3.3
```

## ❌ IF YOU CANNOT UPGRADE (NOT RECOMMENDED)

### Hadoop 3.2.0 Vulnerabilities

1. **Improper Privilege Management** (CVE-2020-9492, CVE-2021-33036, CVE-2022-25168)
   - **Affected**: 3.2.0
   - **Patched**: 3.2.2+
   - **Severity**: High

2. **Argument Injection** (CVE-2022-26612)
   - **Affected**: 3.0.0-alpha to 3.2.3
   - **Patched**: 3.2.4
   - **Severity**: Critical

3. **Heap Overflow** (CVE-2021-37404)
   - **Affected**: 3.0.0 to 3.2.2
   - **Patched**: 3.2.3
   - **Severity**: Critical

4. **Path Traversal** (CVE-2022-26612)
   - **Affected**: 3.2.0 to 3.2.2
   - **Patched**: 3.2.3
   - **Severity**: High

### Spark 3.1.2 Vulnerabilities

1. **Improper Privilege Management** (CVE-2023-32007, CVE-2022-33891)
   - **Affected**: <= 3.3.2
   - **Patched**: 3.3.3
   - **Severity**: High

## 🚨 The Constraint

**Cannot upgrade dependencies** because:
- VM environment has Spark 3.1.2 and Hadoop 3.2.0 installed
- Upgrading causes Protobuf version conflicts
- Results in `ApplicationClientProtocolPB` errors

## ✅ Mitigation Strategy

### 1. Network Isolation
```
Recommendation: Deploy in an isolated network environment
- Use VPC/VLAN isolation
- Restrict inbound/outbound traffic
- Only allow necessary ports (HBase, Redis, SSH)
```

### 2. Access Control
```
Recommendation: Implement strict access controls
- Use Kerberos authentication for Hadoop
- Implement RBAC for Spark
- Restrict file system permissions
- Use dedicated service accounts with minimal privileges
```

### 3. Input Validation
```
Already Implemented in Code:
- File path validation in ImageResourceDownloader
- Input sanitization in GUI components
- Type checking in all processing methods
```

### 4. Monitoring and Auditing
```
Recommendation: Enable comprehensive logging
- Enable Hadoop audit logs
- Enable Spark event logging
- Monitor TaskLogger for suspicious activity
- Set up alerts for privilege escalation attempts
```

### 5. Environment Hardening

#### For Hadoop:
```bash
# In hadoop-env.sh
export HADOOP_OPTS="-Djava.security.manager"
export HADOOP_HEAPSIZE=2048

# In core-site.xml
<property>
  <name>hadoop.security.authorization</name>
  <value>true</value>
</property>
<property>
  <name>hadoop.security.authentication</name>
  <value>kerberos</value>
</property>
```

#### For Spark:
```bash
# In spark-defaults.conf
spark.authenticate true
spark.authenticate.secret <secret>
spark.network.crypto.enabled true
spark.io.encryption.enabled true
```

### 6. Application-Level Protections

**Already Implemented:**
- ✅ Local mode execution (not using YARN by default)
- ✅ Thread pool limits in MapReduceProcessor
- ✅ Input validation in all user-facing methods
- ✅ Exception handling to prevent information leakage
- ✅ Secure temporary file handling

**Additional Recommendations:**
```java
// In ComputeEngineManager.java
public static void setEngine(EngineType engine) {
    // Add security check
    SecurityManager security = System.getSecurityManager();
    if (security != null) {
        security.checkPermission(new RuntimePermission("setEngine"));
    }
    // ... existing code
}
```

### 7. Deployment Guidelines

**DO:**
- ✅ Deploy in a trusted, isolated environment
- ✅ Use firewall rules to restrict access
- ✅ Run with minimal required privileges
- ✅ Regularly backup data
- ✅ Monitor system logs for anomalies
- ✅ Use the MapReduce engine (default) for better isolation

**DON'T:**
- ❌ Expose the application to the public internet
- ❌ Run with root/administrator privileges
- ❌ Process untrusted image files from external sources
- ❌ Share credentials or allow unauthorized access
- ❌ Disable security features for convenience

## 🎯 Risk Assessment

### Current Risk Level: **MEDIUM**

**Factors Reducing Risk:**
1. Application runs in controlled VM environment
2. Uses local mode by default (not distributed YARN)
3. Limited attack surface (GUI application)
4. Input validation implemented
5. No direct network exposure

**Factors Increasing Risk:**
1. Unpatched vulnerabilities in dependencies
2. Potential privilege escalation vectors
3. Heap overflow possibilities
4. Path traversal vulnerabilities

## 📋 Action Items for Deployment

### Immediate Actions (Required)
1. [ ] Deploy in isolated network segment
2. [ ] Configure firewall rules
3. [ ] Set up minimal privilege service account
4. [ ] Enable Hadoop/Spark audit logging
5. [ ] Document security configuration

### Short-term Actions (Recommended)
1. [ ] Implement Kerberos authentication
2. [ ] Set up monitoring and alerting
3. [ ] Create security incident response plan
4. [ ] Regular security audits
5. [ ] Backup and disaster recovery procedures

### Long-term Actions (Strategic)
1. [ ] Request VM environment upgrade to patched versions
2. [ ] Evaluate alternative architectures
3. [ ] Implement defense-in-depth strategy
4. [ ] Regular penetration testing
5. [ ] Security training for operators

## 🔐 Security Checklist for Operators

Before deploying:
- [ ] System is in isolated network
- [ ] Firewall configured
- [ ] Service account created with minimal privileges
- [ ] Audit logging enabled
- [ ] All default passwords changed
- [ ] Security monitoring in place
- [ ] Backup system configured
- [ ] Incident response plan documented
- [ ] Only processing trusted image sources
- [ ] Regular security updates scheduled

## 📞 Incident Response

If a security incident occurs:
1. Immediately isolate the affected system
2. Preserve logs and evidence
3. Review TaskLogger for suspicious activity
4. Check for unauthorized privilege escalation
5. Verify data integrity
6. Follow organization's incident response procedures

## ⚖️ Trade-off Acceptance

**Accepting Known Vulnerabilities:**
This decision is made due to operational constraints (VM environment limitations). The acceptance is conditional on implementing the mitigations described above.

**Signed off by:** Development Team
**Date:** 2026-01-13
**Review Date:** 2026-04-13 (90 days)

## 📚 References

- [CVE-2020-9492](https://nvd.nist.gov/vuln/detail/CVE-2020-9492) - Hadoop Privilege Management
- [CVE-2022-26612](https://nvd.nist.gov/vuln/detail/CVE-2022-26612) - Hadoop Argument Injection
- [CVE-2021-37404](https://nvd.nist.gov/vuln/detail/CVE-2021-37404) - Hadoop Heap Overflow
- [CVE-2023-32007](https://nvd.nist.gov/vuln/detail/CVE-2023-32007) - Spark Privilege Management
- [Apache Hadoop Security](https://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-common/SecureMode.html)
- [Apache Spark Security](https://spark.apache.org/docs/latest/security.html)
