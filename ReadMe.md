## Deprecated - please now use [kansible](https://github.com/fabric8io/kansible)

**NOTE** this project is now deprecated and replaced by [kansible](https://github.com/fabric8io/kansible)!

We've figured out a much simpler and cleaner way of using non-docker processes on Windows, AIX, Solaris, HPUX and old linuxes which makes it easier to mix and match native operating systems proceesses with docker containers.

So please take a look at [kansible](https://github.com/fabric8io/kansible) as a nicer alternative to Jube!

[kansible](https://github.com/fabric8io/kansible) is better than Jube because:

* kansible provides a single pane of glass for all your operating system processes on windows and Unix along with all your linux based docker containers
* kansible lets you reuse all Kubernetes features for your operating system processes too:
  * service discovery using DNS
  * high availability and scaling manually or automatically through metrics
  * liveness and readiness checks
  * centralised logging and metrics
* kansible also lets you use the power of Ansible playbooks to provision your software, install JDKs, create user accounts and so forth 
