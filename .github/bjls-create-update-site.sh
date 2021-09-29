# Script to create the update site for official releases

mkdir bjls-staging
mkdir bjls-staging/bjls
mkdir bjls-staging/bjls/bjls-update-site
cp releng/p2repository/target/*.zip bjls-staging/bjls
cp -R releng/p2repository/target/repository bjls-staging/bjls/bjls-update-site
cp .github/bjls-update-site-index.html bjls-staging/bjls/index.html
