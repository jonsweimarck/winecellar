/*
    Svenska felmeddelanden för webbläsarens inbyggda formulärvalidering.

    Standardtexterna ("Please fill out this field.") kommer från
    WEBBLÄSARENS språkinställning, inte från sidans lang-attribut, och går
    inte att översätta med markup. Enda vägen är setCustomValidity, som
    ersätter texten i webbläsarens egen bubbla.

    FÄLLA att känna till om den här filen ändras: ett fält med en satt
    custom-validity räknas som ogiltigt tills meddelandet nollställs -
    även efter att användaren fyllt i det korrekt. Glöms nollställningen
    går formuläret ALDRIG att skicka. Därför nollställer uppdatera()
    alltid först, och sätter ett meddelande först efter att de inbyggda
    reglerna fått avgöra på nytt.
*/
(function () {
    function meddelandeFor(falt) {
        if (falt.validity.valueMissing) {
            return falt.type === 'file' ? 'Välj en fil.' : 'Fyll i det här fältet.';
        }
        if (falt.validity.rangeUnderflow) {
            return 'Ange ett värde som är minst ' + falt.min + '.';
        }
        if (falt.validity.rangeOverflow) {
            return 'Ange ett värde som är högst ' + falt.max + '.';
        }
        if (falt.validity.badInput) {
            return 'Ange ett tal.';
        }
        // Övriga fall (mönster, e-post m.m.) används inte i appen i dag -
        // tom sträng låter webbläsarens egen text stå kvar i stället för
        // att hitta på en text som inte beskriver felet.
        return '';
    }

    function uppdatera(falt) {
        falt.setCustomValidity('');
        if (!falt.validity.valid) {
            falt.setCustomValidity(meddelandeFor(falt));
        }
    }

    document.querySelectorAll('input, select, textarea').forEach(function (falt) {
        // `invalid` hinner före webbläsarens bubbla - texten som sätts här
        // är den som faktiskt visas.
        falt.addEventListener('invalid', function () {
            uppdatera(falt);
        });
        falt.addEventListener('input', function () {
            uppdatera(falt);
        });
        falt.addEventListener('change', function () {
            uppdatera(falt);
        });
    });
})();
