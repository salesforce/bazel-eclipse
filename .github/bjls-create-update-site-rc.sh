# Script to create the update site for BJLS test release candidates

mkdir bjls-rc-staging
mkdir bjls-rc-staging/bjls
mkdir bjls-rc-staging/bjls/bjls-update-site-rc
cp releng/p2repository/target/*.zip bjls-rc-staging/bjls
cp -R releng/p2repository/target/repository bjls-rc-staging/bjls/bjls-update-site-rc
cp .github/bjls-update-site-index.html bjls-rc-staging/bjls/index.html
