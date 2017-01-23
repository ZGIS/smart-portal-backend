
PROFILENOPASS='{"email":"alex@example.com","email":"alex","firstname":"Alex","lastname":"K"}'
LOGIN='{"email":"alex","password":"testpass123"}'
FULLPROFILE='{"email":"a.kmoch@gns.cri.nz","email":"akmoch","firstname":"Alex","lastname":"K","password":"testpass"}'
GOOGLEPAYLOAD="Google OAuth Payload"

# --cookie "TOKEN=svdfbvd"
USERNAME=alex
COOKIE='XSRF-TOKEN=DRF$ZJNU'
AUTH_HEADER="X-XSRF-TOKEN: DRF$ZJNU"
REGLINK="4945f7b5-b16f-42f4-98e2-2603ea027a03"

curl -v -XPOST http://localhost:9000/api/v1/login -H "Content-type: application/json" -d "$LOGIN" --cookie-jar cookie-jar.txt
# receives cookie

curl -v -XPOST http://localhost:9000/api/v1/login/gconnect -H "Content-type: application/json" -d "$GOOGLEPAYLOAD" --cookie-jar cookie-jar.txt
# receives cookie

curl -v -XGET http://localhost:9000/api/v1/logout --cookie cookie-jar.txt -H "$AUTH_HEADER"
curl -v -XPOST http://localhost:9000/api/v1/logout --cookie cookie-jar.txt  -H "$AUTH_HEADER"
curl -v -XGET http://localhost:9000/api/v1/logout/gdisconnect --cookie cookie-jar.txt -H "$AUTH_HEADER"

curl -v -XGET http://localhost:9000/api/v1/users/self --cookie "$COOKIE"  -H "$AUTH_HEADER"
curl -v -XGET http://localhost:9000/api/v1/users/delete/${USERNAME}  --cookie cookie-jar.txt  -H "$AUTH_HEADER"
curl -v -XGET http://localhost:9000/api/v1/users/profile/${USERNAME}  --cookie cookie-jar.txt  -H "$AUTH_HEADER"
curl -v -XPOST http://localhost:9000/api/v1/users/update/${USERNAME} -H "Content-type: application/json" -d "$PROFILENOPASS"  --cookie cookie-jar.txt  -H "$AUTH_HEADER"
curl -v -XPOST http://localhost:9000/api/v1/users/update/${USERNAME} -H "Content-type: application/json" -d "$FULLPROFILE"  --cookie cookie-jar.txt  --cookie-jar cookie-jar.txt -H "$AUTH_HEADER"
# should receive new cookie?

curl -v -XPOST http://localhost:9000/api/v1/users/register -H "Content-type: application/json" -d "$FULLPROFILE"

curl -v -XGET http://localhost:9000/api/v1/users/register/$REGLINK --cookie-jar cookie-jar.txt
# receives cookie



