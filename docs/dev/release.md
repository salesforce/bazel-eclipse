## Releasing Bazel Eclipse

This document is for maintainers.
This is the set of steps for releasing a new version of BEF.

## Release Process

### Setting the Version

We follow SemVer.
Determine what type of release this must be (major, minor, patch) given the changes
  that are in master.
That will dictate what version number to use for the new release.

The version is set in a number of files, both for Maven and for OSGi (Eclipse).
For Maven pom.xml files, keep the *SNAPSHOT* suffix (e.g. *1.3.1-SNAPSHOT*).
For OSGi meta files, keep the *qualifier* suffix (e.g. *1.3.1.qualifier*).

Tycho has commands for this, and is a good start, but they don't do the full job:
```
 mvn -X -DnewVersion=1.3.1-SNAPSHOT tycho-versions:set-version
 mvn -X tycho-versions:update-eclipse-metadata
```

In the end, you will need to manually ensure the new version is applied everywhere.
This [PR for version 1.3.1](https://github.com/salesforce/bazel-eclipse/pull/225/files)
  gives you an idea.
Grepping through the code base looking for the old versions is not a bad idea.

Verify all the files are in order before submitting to master with this command:
```
mvn clean verify
```

### Local Build and Testing

Prior to releasing BEF, please test it from a fresh install of standard Eclipse (not an SDK version).
Launching BEF (as inner eclipse) from the Eclipse SDK (outer eclipse) is not a sufficient test.
Certain packaging problems will not appear when launched from Eclipse SDK.
Instead, please download the latest JEE Eclipse to test with.

The build will write the built feature into the _releng/p2repository/target/_ directory.
You can install from the archive zip in that location.
Use the standard Eclipse _Help -> Install New Software..._ menu and choose to _Add_ an _Archive_.

Make sure to run through an import of a decently complex Bazel workspace.
Things to look for:
- The Bazel Classpath is correct for each project
- There are no build errors in the Problems View
- Open a .java file in an editor an make sure there aren't any red squigglies

### Releasing from GitHub to the Update Site

Note that we have two update sites, one for the official releases and one for release candidates.
Deploy a release candidate and test it prior to releasing the official release.

- [Update Instructions](https://opensource.salesforce.com/bazel-eclipse/) are written [here](https://github.com/salesforce/bazel-eclipse/blob/master/.github/update-site-index.html)
- Official release update site: https://opensource.salesforce.com/bazel-eclipse/update-site
- Release candidate (RC) update site: http://opensource.salesforce.com/bazel-eclipse/update-site-release-candidate

:fire: The update site is our live installation location for our users. If you are doing
experimental work with the release process, please do not release directly to _gh-pages_
branch.

- Issue a release candidate prior to deploying an official release
- Update the _github-pages-deploy-action_ stage in
[the workflow](../../.github/workflows/build-release-deploy.yml) to deploy to a test
branch if you have **any** doubts about what you are releasing.
- Be prepared to rollback the [gh-pages branch](https://github.com/salesforce/bazel-eclipse/tree/gh-pages)
at the first sign of trouble (see steps on how to do this in next section).

Our GitHub repo has our release process implemented as a GitHub Actions workflow.
To build the release, make sure you have your versions set and committed to master (see above).

Then, run the release workflow:
 - Navigate to the [Bazel Eclipse Actions](https://github.com/salesforce/bazel-eclipse/actions) tab in GitHub
 - Click on the _Bazel Eclipse Feature Release_ or _Bazel Eclipse Feature Test Release Candidate_ button in the left nav
 - Click on the _Run Workflow_ button on the right side, and pick the branch to release from (normally _master_)
 - Monitor the build, it should take about a minute
 - Navigate to the [gh-pages branch](https://github.com/salesforce/bazel-eclipse/tree/gh-pages) of the repo. It contains all the files from the release.
   - Verify the files look to be correct (i.e. check version in the filenames)

### Test the Update Site

First, check that our landing page is available and has correct instructions:

- [BEF Update Site landing page](https://opensource.salesforce.com/bazel-eclipse/)

Next, follow the instructions on the page and install BEF into a fresh copy of Eclipse.
If you are testing a release candidate, remember to use the RC update site http://opensource.salesforce.com/bazel-eclipse/update-site-release-candidate.
Finally, repeat the install test with the zip file that is in the root directory of the *gh-pages* branch

If anything fails, revert the changes in the _gh-pages_ branch ASAP!
It is the live update site for all of our users.

```
# assume release 1.3.2 has a problem, revert gh-pages back to 1.3.1
git checkout gh-pages
git reset --hard 1.3.1.updatesite
git push --force origin gh-pages
```

### Creating the Official Release Record

After the release build has been confirmed, please create a new release in our
  [Releases](https://github.com/salesforce/bazel-eclipse/releases) list.
Click the _Draft a new release_ button to get started.

Please follow the pattern of previous releases.
Be sure to list the changes and give credit to outside contributors.
Especially look at the naming convention of the archive file, as in _bazel-eclipse-feature-1.3.1-release.zip_.

As part of the release, you will create a tag.
Follow our convention of using the bare SemVer, like _1.3.1_.

### Tag the Update Site

In the previous step, you tagged the _master_ branch as a side effect of creating the release record.
We also want to tag the update site, as we may need to rollback if the next release fails.
This is done by tagging the _gh-pages_ branch.

```
git checkout gh-pages
git pull
git tag 1.3.1.updatesite
git push origin 1.3.1.updatesite
```

### Update the Eclipse Marketplace Record

For each release, we need to add an entry to our Marketplace record.
The BEF record is here, which will have an _Edit_ button if you are entitled:

- [BEF in the Eclipse Marketplace](https://marketplace.eclipse.org/content/bazel-eclipse-feature)

## Release Implementation

The release workflow is defined using GitHub Actions.
Instead of explaining how it works, here are some pointers to some interesting files:

- [Release workflow definition](../../.github/workflows/build-release-deploy.yml)
- [Update site layout script](../../.github/create-update-site.sh)
- [Update site layout script for RCs](../../.github/create-update-site-rc.sh)
