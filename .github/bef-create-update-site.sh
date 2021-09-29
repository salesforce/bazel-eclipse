# Script to create the update site for official releases

mkdir bef-staging
cp releng/p2repository/target/*.zip bef-staging
cp -R releng/p2repository/target/repository bef-staging/update-site
cp .github/bef-update-site-index.html bef-staging/index.html
