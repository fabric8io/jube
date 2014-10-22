## Differences between Jube and the Go based Kubernetes / OpenShift V3

Jube is a pure Java implementation of kubernetes; designed for use provisioning Java based middleware and applications when either Go Lang, Docker or Kubernetes is not available on your platform.

While it tries to keep as close as possible to the Kubernetes REST API and metadata it does have some differences:

* Jube is implemented in pure Java; intended for use in non-modern-linux platforms where any of Go Lang, Docker or Kubernetes is not natively supported well. We recommened you use Docker and Kubernetes for real whenever you can though!
* Since its pure Java, Jube cannot do any funky iptables / OpenVSwitch stuff; so the kubernetes networking model is a little different in that:
  * we cannot allocate an IP port per Pod or Service
  * service ports are global across all services; so please be careful to avoid clashes!
* Jube does not use Docker (since if you had docker, we recommend you use Kubernetes for real ;). So cannot run arbitrary linux images. It can run [image zips](imageZips.html) which are similar conceptually; some files used to run a process. This is fine for running simple binaries and Java applications; but is not ideal if you wish to use things like package managers and RPMs per container (e.g. for working with Ruby, Python, Node etc). For those kinds of things we highly recommend Docker and Kubernetes. The focus of Jube is predominantly around running Java based middleware and applications; were an image zip is OK (when docker is not available)
