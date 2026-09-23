/*
    Delad "levande" väntetextanimation (WINE-54) för de statusrader som
    visas medan ett LLM-anrop pågår - etikettskanningen (#etikett-status,
    vin-formular.html) och chattens två formulär (#ny-konversation-status,
    chatt-lista.html; #nytt-meddelande-status, chatt.html). Innan detta
    stod texten still ("Analyserar etikett...") under hela väntetiden,
    vilket kunde se overksamt/fruset ut.

    Samtliga tre ställen är "fire and forget"-statusar inför en vanlig,
    fullständig sidnavigering (ingen htmx/fetch att invänta i JS) - så fort
    formuläret skickas in väntar webbläsaren in HELA svaret (etikettolkning
    respektive LLM-svar) innan sidan byts ut. Animationen måste därför vara
    en ren, självgående setInterval-loop som bara körs fram tills
    webbläsaren navigerar bort - ingen clearInterval behövs, navigeringen
    tar hand om det åt oss.

    setInterval + textContent valdes framför en CSS @keyframes-animation av
    content-egenskapen (tekniskt möjligt i moderna webbläsare) eftersom
    story WINE-54 uttryckligen kräver att det fungerar på både dator och
    mobil, och JS-vägen inte är beroende av hur pass nya webbläsarversioner
    som råkar finnas på en given telefon.
*/
(function () {
    var INTERVALL_MS = 500;
    var MAX_PUNKTER = 3;

    // Startar en evig 1-2-3-punkter-loop efter grundtexten på `element`.
    // `grundtext` ska INTE ha egna avslutande punkter - de läggs på och
    // cyklas av loopen. Sätter texten synkront innan loopen ens startar,
    // så elementet aldrig är tomt/utan punkter mellan att den här
    // funktionen anropas och första intervall-tick:et.
    //
    // Granskningsfynd (PR #39): städar bort en ev. redan pågående loop på
    // SAMMA element innan en ny startas (id sparat direkt på elementet,
    // via ett dataset-attribut) - annars kunde t.ex. ett andra filval i
    // vin-formular.html (innan det första hunnit klart) starta en andra,
    // oberoende setInterval-loop på samma statusrad. Ofarligt i sig, men
    // obegränsad tillväxt av parallella loopar utan städning annars.
    function starta(element, grundtext) {
        if (!element) {
            return;
        }
        if (element.dataset.väntestatusIntervall) {
            clearInterval(Number(element.dataset.väntestatusIntervall));
        }
        var antalPunkter = 1;
        var uppdatera = function () {
            element.textContent = grundtext + '.'.repeat(antalPunkter);
            antalPunkter = antalPunkter === MAX_PUNKTER ? 1 : antalPunkter + 1;
        };
        uppdatera();
        element.dataset.väntestatusIntervall = String(setInterval(uppdatera, INTERVALL_MS));
    }

    window.winecellarVäntestatus = {starta: starta};
})();
