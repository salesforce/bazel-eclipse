# Script to create the update site for BJLS test release candidates

mkdir bjls-update-site-rc
cp releng/p2repository/target/*.zip bjls-update-site-rc
cp -R releng/p2repository/target/repository bjls-update-site-rc/update-site-release-candidate
cp .github/bjls-update-site-index.html bjls-update-site-rc/index.html
