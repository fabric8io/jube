## Goals

The goals of Jube are to try increase the adoption of Kubernetes concepts in managing containers (pods, replication controllers, services); whether folks are using modern linux or have Go Lang or Docker support or not.

The idea is that if you are running on a platform that is not yet supported by Kubernetes / Docker / Go Lang; you can still work in a kubernetes-like way using Jube's pure Java implementation of the Kubernetes model and REST API.

If you are using Linux we highly recommend you use [Kubernetes](http://kubernetes.io/) and [Docker](http://docker.com)! However if you must run Java middleware on non-Linux platforms then Jube can help provide a kubernetes-like experience on platforms where [golang](https://golang.org/), [Kubernetes](http://kubernetes.io/) or [Docker](http://docker.com) are not supported,

### Aims

* To reuse your [Kuberentes Apps](http://fabric8.io/v2/apps.html) and [App Zips](http://fabric8.io/v2/appzip.html) on platforms not supported by Kubernetes/Docker
* To allow you to any Kubernetes based tools which use the Kubernetes REST API (for provisioning or monitoring) when using pure Java.

