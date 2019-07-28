import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
admin.initializeApp();

// // Start writing Firebase Functions
// // https://firebase.google.com/docs/functions/typescript

export const onAuth = functions.auth.user().onCreate((usuario, context) => {
    return admin.database().ref("users/"+usuario.uid).set({
        "watchVideo": false, "Coins" : 0, "CountVideos": 0
    })
}) 

export const onvideoWatched = functions.https.onCall((data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError('failed-precondition', 'The function must be called while authenticated.');
    }
    
    const userID = context.auth.uid;
    return admin.database().ref(`/users/${userID}`).once("value").then(snapshot => {
        const dataUser = snapshot.val();

        return admin.database().ref(`/users/${userID}`).update({
            'watchVideo': true, 'CountVideos': parseInt(dataUser.CountVideos) + 1
        })
    }).catch(err => {
        console.log(err)
        throw new functions.https.HttpsError('aborted', `Error actualizando datos de usuario ${userID}`);
    })
}) 
