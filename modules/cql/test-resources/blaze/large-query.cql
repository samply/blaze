library Retrieve version '1.0.0'
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0'

codesystem atc: 'http://fhir.de/CodeSystem/bfarm/atc'
codesystem icd10: 'http://fhir.de/CodeSystem/bfarm/icd-10-gm'
codesystem oops: 'http://fhir.de/CodeSystem/bfarm/ops'

context Unfiltered

define EnasidenibL01XX59Ref:
  from [Medication: Code 'L01XX59' from atc] M
    return 'Medication/' + M.id

define PamiparibL01XK06Ref:
  from [Medication: Code 'L01XK06' from atc] M
    return 'Medication/' + M.id

define "Pflanzliche Alkaloide Und Andere Natürliche MittelL01CRef":
  from [Medication: Code 'L01C' from atc] M
    return 'Medication/' + M.id

define PixantronL01DB11Ref:
  from [Medication: Code 'L01DB11' from atc] M
    return 'Medication/' + M.id

define PanitumumabL01FE02Ref:
  from [Medication: Code 'L01FE02' from atc] M
    return 'Medication/' + M.id

define "Hormonantagonisten Und Verwandte MittelL02BRef":
  from [Medication: Code 'L02B' from atc] M
    return 'Medication/' + M.id

define CemiplimabL01FF06Ref:
  from [Medication: Code 'L01FF06' from atc] M
    return 'Medication/' + M.id

define PralatrexatL01BA05Ref:
  from [Medication: Code 'L01BA05' from atc] M
    return 'Medication/' + M.id

define EfaproxiralL01XD06Ref:
  from [Medication: Code 'L01XD06' from atc] M
    return 'Medication/' + M.id

define BelinostatL01XH04Ref:
  from [Medication: Code 'L01XH04' from atc] M
    return 'Medication/' + M.id

define AbarelixL02BX01Ref:
  from [Medication: Code 'L02BX01' from atc] M
    return 'Medication/' + M.id

define ChlorambucilL01AA02Ref:
  from [Medication: Code 'L01AA02' from atc] M
    return 'Medication/' + M.id

define TepotinibL01EX21Ref:
  from [Medication: Code 'L01EX21' from atc] M
    return 'Medication/' + M.id

define TebentafuspL01XX75Ref:
  from [Medication: Code 'L01XX75' from atc] M
    return 'Medication/' + M.id

define "Enfortumab vedotinL01FX13Ref":
  from [Medication: Code 'L01FX13' from atc] M
    return 'Medication/' + M.id

define PacritinibL01EJ03Ref:
  from [Medication: Code 'L01EJ03' from atc] M
    return 'Medication/' + M.id

define "Proteinkinase-InhibitorenL01ERef":
  from [Medication: Code 'L01E' from atc] M
    return 'Medication/' + M.id

define IrinotecanL01CE02Ref:
  from [Medication: Code 'L01CE02' from atc] M
    return 'Medication/' + M.id

define AnsuvimabJ06BD04Ref:
  from [Medication: Code 'J06BD04' from atc] M
    return 'Medication/' + M.id

define "CD38 (Cluster of Differentiation 38)-InhibitorenL01FCRef":
  from [Medication: Code 'L01FC' from atc] M
    return 'Medication/' + M.id

define VinorelbinL01CA04Ref:
  from [Medication: Code 'L01CA04' from atc] M
    return 'Medication/' + M.id

define VorozolL02BG05Ref:
  from [Medication: Code 'L02BG05' from atc] M
    return 'Medication/' + M.id

define MitotanL01XX23Ref:
  from [Medication: Code 'L01XX23' from atc] M
    return 'Medication/' + M.id

define DemecolcinL01CC01Ref:
  from [Medication: Code 'L01CC01' from atc] M
    return 'Medication/' + M.id

define BermekimabL01FX11Ref:
  from [Medication: Code 'L01FX11' from atc] M
    return 'Medication/' + M.id

define CyclophosphamidL01AA01Ref:
  from [Medication: Code 'L01AA01' from atc] M
    return 'Medication/' + M.id

define DacomitinibL01EB07Ref:
  from [Medication: Code 'L01EB07' from atc] M
    return 'Medication/' + M.id

define TagraxofuspL01XX67Ref:
  from [Medication: Code 'L01XX67' from atc] M
    return 'Medication/' + M.id

define PanobinostatL01XH03Ref:
  from [Medication: Code 'L01XH03' from atc] M
    return 'Medication/' + M.id

define PyrantelP02CC01Ref:
  from [Medication: Code 'P02CC01' from atc] M
    return 'Medication/' + M.id

define "Diphtherie-ImmunglobulinJ06BB10Ref":
  from [Medication: Code 'J06BB10' from atc] M
    return 'Medication/' + M.id

define BortezomibL01XG01Ref:
  from [Medication: Code 'L01XG01' from atc] M
    return 'Medication/' + M.id

define TrastuzumabL01FD01Ref:
  from [Medication: Code 'L01FD01' from atc] M
    return 'Medication/' + M.id

define VinfluninL01CA05Ref:
  from [Medication: Code 'L01CA05' from atc] M
    return 'Medication/' + M.id

define "Andere BandwurmmittelP02DXRef":
  from [Medication: Code 'P02DX' from atc] M
    return 'Medication/' + M.id

define "Platin-haltige VerbindungenL01XARef":
  from [Medication: Code 'L01XA' from atc] M
    return 'Medication/' + M.id

define AnthelminthikaP02Ref:
  from [Medication: Code 'P02' from atc] M
    return 'Medication/' + M.id

define PalbociclibL01EF01Ref:
  from [Medication: Code 'L01EF01' from atc] M
    return 'Medication/' + M.id

define TafasitamabL01FX12Ref:
  from [Medication: Code 'L01FX12' from atc] M
    return 'Medication/' + M.id

define TestolactonL02BG09Ref:
  from [Medication: Code 'L02BG09' from atc] M
    return 'Medication/' + M.id

define ParsaclisibL01EM05Ref:
  from [Medication: Code 'L01EM05' from atc] M
    return 'Medication/' + M.id

define MitoguazonL01XX16Ref:
  from [Medication: Code 'L01XX16' from atc] M
    return 'Medication/' + M.id

define "Tollwut-ImmunglobulinJ06BB05Ref":
  from [Medication: Code 'J06BB05' from atc] M
    return 'Medication/' + M.id

define DabrafenibL01EC02Ref:
  from [Medication: Code 'L01EC02' from atc] M
    return 'Medication/' + M.id

define TrofosfamidL01AA07Ref:
  from [Medication: Code 'L01AA07' from atc] M
    return 'Medication/' + M.id

define CladribinL01BB04Ref:
  from [Medication: Code 'L01BB04' from atc] M
    return 'Medication/' + M.id

define AvapritinibL01EX18Ref:
  from [Medication: Code 'L01EX18' from atc] M
    return 'Medication/' + M.id

define "Janus-assoziierte Kinase (JAK)-InhibitorenL01EJRef":
  from [Medication: Code 'L01EJ' from atc] M
    return 'Medication/' + M.id

define "Nivolumab und RelatlimabL01XY03Ref":
  from [Medication: Code 'L01XY03' from atc] M
    return 'Medication/' + M.id

define ProlgolimabL01FF08Ref:
  from [Medication: Code 'L01FF08' from atc] M
    return 'Medication/' + M.id

define AvelumabL01FF04Ref:
  from [Medication: Code 'L01FF04' from atc] M
    return 'Medication/' + M.id

define EtoposidL01CB01Ref:
  from [Medication: Code 'L01CB01' from atc] M
    return 'Medication/' + M.id

define "Immunsera Und ImmunglobulineJ06Ref":
  from [Medication: Code 'J06' from atc] M
    return 'Medication/' + M.id

define "Tetrahydropyrimidin-DerivateP02CCRef":
  from [Medication: Code 'P02CC' from atc] M
    return 'Medication/' + M.id

define MobocertinibL01EB10Ref:
  from [Medication: Code 'L01EB10' from atc] M
    return 'Medication/' + M.id

define EpirubicinL01DB03Ref:
  from [Medication: Code 'L01DB03' from atc] M
    return 'Medication/' + M.id

define "Fluorouracil, KombinationenL01BC52Ref":
  from [Medication: Code 'L01BC52' from atc] M
    return 'Medication/' + M.id

define EntinostatL01XH05Ref:
  from [Medication: Code 'L01XH05' from atc] M
    return 'Medication/' + M.id

define EstrogeneL02AARef:
  from [Medication: Code 'L02AA' from atc] M
    return 'Medication/' + M.id

define CabozantinibL01EX07Ref:
  from [Medication: Code 'L01EX07' from atc] M
    return 'Medication/' + M.id

define TegafurL01BC03Ref:
  from [Medication: Code 'L01BC03' from atc] M
    return 'Medication/' + M.id

define "Anthracycline und verwandte SubstanzenL01DBRef":
  from [Medication: Code 'L01DB' from atc] M
    return 'Medication/' + M.id

define CytarabinL01BC01Ref:
  from [Medication: Code 'L01BC01' from atc] M
    return 'Medication/' + M.id

define AntiestrogeneL02BARef:
  from [Medication: Code 'L02BA' from atc] M
    return 'Medication/' + M.id

define "Axicabtagen ciloleucelL01XX70Ref":
  from [Medication: Code 'L01XX70' from atc] M
    return 'Medication/' + M.id

define "Mumps-ImmunglobulinJ06BB15Ref":
  from [Medication: Code 'J06BB15' from atc] M
    return 'Medication/' + M.id

define DiethylcarbamazinP02CB02Ref:
  from [Medication: Code 'P02CB02' from atc] M
    return 'Medication/' + M.id

define MistelkrautL01CP01Ref:
  from [Medication: Code 'L01CP01' from atc] M
    return 'Medication/' + M.id

define CapmatinibL01EX17Ref:
  from [Medication: Code 'L01EX17' from atc] M
    return 'Medication/' + M.id

define FlutamidL02BB01Ref:
  from [Medication: Code 'L02BB01' from atc] M
    return 'Medication/' + M.id

define "Allogene T-Zellen, genetisch modifiziertL01XX90Ref":
  from [Medication: Code 'L01XX90' from atc] M
    return 'Medication/' + M.id

define SonidegibL01XJ02Ref:
  from [Medication: Code 'L01XJ02' from atc] M
    return 'Medication/' + M.id

define TalazoparibL01XK04Ref:
  from [Medication: Code 'L01XK04' from atc] M
    return 'Medication/' + M.id

define TamoxifenL02BA01Ref:
  from [Medication: Code 'L02BA01' from atc] M
    return 'Medication/' + M.id

define "Moxetumomab pasudotoxL01FB02Ref":
  from [Medication: Code 'L01FB02' from atc] M
    return 'Medication/' + M.id

define MegestrolL02AB01Ref:
  from [Medication: Code 'L02AB01' from atc] M
    return 'Medication/' + M.id

define ToremifenL02BA02Ref:
  from [Medication: Code 'L02BA02' from atc] M
    return 'Medication/' + M.id

define "Proteasom-InhibitorenL01XGRef":
  from [Medication: Code 'L01XG' from atc] M
    return 'Medication/' + M.id

define "Hormone Und Verwandte MittelL02ARef":
  from [Medication: Code 'L02A' from atc] M
    return 'Medication/' + M.id

define "Benzimidazol-DerivateP02CARef":
  from [Medication: Code 'P02CA' from atc] M
    return 'Medication/' + M.id

define PolyestradiolphosphatL02AA02Ref:
  from [Medication: Code 'L02AA02' from atc] M
    return 'Medication/' + M.id

define NivolumabL01FF01Ref:
  from [Medication: Code 'L01FF01' from atc] M
    return 'Medication/' + M.id

define ChlorotrianisenL02AA05Ref:
  from [Medication: Code 'L02AA05' from atc] M
    return 'Medication/' + M.id

define MetrifonatP02BB01Ref:
  from [Medication: Code 'P02BB01' from atc] M
    return 'Medication/' + M.id

define BezlotoxumabJ06BC03Ref:
  from [Medication: Code 'J06BC03' from atc] M
    return 'Medication/' + M.id

define DurvalumabL01FF03Ref:
  from [Medication: Code 'L01FF03' from atc] M
    return 'Medication/' + M.id

define TemoporfinL01XD05Ref:
  from [Medication: Code 'L01XD05' from atc] M
    return 'Medication/' + M.id

define MogamulizumabL01FX09Ref:
  from [Medication: Code 'L01FX09' from atc] M
    return 'Medication/' + M.id

define PemetrexedL01BA04Ref:
  from [Medication: Code 'L01BA04' from atc] M
    return 'Medication/' + M.id

define "Tegafur, KombinationenL01BC53Ref":
  from [Medication: Code 'L01BC53' from atc] M
    return 'Medication/' + M.id

define "Tegafur, Gimeracil und OteracilL01BC73Ref":
  from [Medication: Code 'L01BC73' from atc] M
    return 'Medication/' + M.id

define "Kuhpocken-ImmunglobulinJ06BB07Ref":
  from [Medication: Code 'J06BB07' from atc] M
    return 'Medication/' + M.id

define SorafenibL01EX02Ref:
  from [Medication: Code 'L01EX02' from atc] M
    return 'Medication/' + M.id

define AvermektineP02CFRef:
  from [Medication: Code 'P02CF' from atc] M
    return 'Medication/' + M.id

define AtezolizumabL01FF05Ref:
  from [Medication: Code 'L01FF05' from atc] M
    return 'Medication/' + M.id

define "VEGF/VEGFR (Vaskulärer endothelialer Wachstumsfaktor)-InhibitorenL01FGRef":
  from [Medication: Code 'L01FG' from atc] M
    return 'Medication/' + M.id

define PadeliporfinL01XD07Ref:
  from [Medication: Code 'L01XD07' from atc] M
    return 'Medication/' + M.id

define PertuzumabL01FD02Ref:
  from [Medication: Code 'L01FD02' from atc] M
    return 'Medication/' + M.id

define "Alkylierende MittelL01ARef":
  from [Medication: Code 'L01A' from atc] M
    return 'Medication/' + M.id

define IdelalisibL01EM01Ref:
  from [Medication: Code 'L01EM01' from atc] M
    return 'Medication/' + M.id

define "Trastuzumab emtansinL01FD03Ref":
  from [Medication: Code 'L01FD03' from atc] M
    return 'Medication/' + M.id

define IsatuximabL01FC02Ref:
  from [Medication: Code 'L01FC02' from atc] M
    return 'Medication/' + M.id

define "Histon-Deacetylase (HDAC)-InhibitorenL01XHRef":
  from [Medication: Code 'L01XH' from atc] M
    return 'Medication/' + M.id

define BexarotenL01XF03Ref:
  from [Medication: Code 'L01XF03' from atc] M
    return 'Medication/' + M.id

define MethoxsalenL01XD10Ref:
  from [Medication: Code 'L01XD10' from atc] M
    return 'Medication/' + M.id

define SelpercatinibL01EX22Ref:
  from [Medication: Code 'L01EX22' from atc] M
    return 'Medication/' + M.id

define CabazitaxelL01CD04Ref:
  from [Medication: Code 'L01CD04' from atc] M
    return 'Medication/' + M.id

define PembrolizumabL01FF02Ref:
  from [Medication: Code 'L01FF02' from atc] M
    return 'Medication/' + M.id

define DegarelixL02BX02Ref:
  from [Medication: Code 'L02BX02' from atc] M
    return 'Medication/' + M.id

define MiltefosinL01XX09Ref:
  from [Medication: Code 'L01XX09' from atc] M
    return 'Medication/' + M.id

define AmrubicinL01DB10Ref:
  from [Medication: Code 'L01DB10' from atc] M
    return 'Medication/' + M.id

define DichlorophenP02DX02Ref:
  from [Medication: Code 'P02DX02' from atc] M
    return 'Medication/' + M.id

define EnzalutamidL02BB04Ref:
  from [Medication: Code 'L02BB04' from atc] M
    return 'Medication/' + M.id

define NintedanibL01EX09Ref:
  from [Medication: Code 'L01EX09' from atc] M
    return 'Medication/' + M.id

define DuvelisibL01EM04Ref:
  from [Medication: Code 'L01EM04' from atc] M
    return 'Medication/' + M.id

define GlasdegibL01XJ03Ref:
  from [Medication: Code 'L01XJ03' from atc] M
    return 'Medication/' + M.id

define CopanlisibL01EM02Ref:
  from [Medication: Code 'L01EM02' from atc] M
    return 'Medication/' + M.id

define LonidaminL01XX07Ref:
  from [Medication: Code 'L01XX07' from atc] M
    return 'Medication/' + M.id

define TrametinibL01EE01Ref:
  from [Medication: Code 'L01EE01' from atc] M
    return 'Medication/' + M.id

define "Röteln-ImmunglobulinJ06BB06Ref":
  from [Medication: Code 'J06BB06' from atc] M
    return 'Medication/' + M.id

define SatraplatinL01XA04Ref:
  from [Medication: Code 'L01XA04' from atc] M
    return 'Medication/' + M.id

define PipobromanL01AX02Ref:
  from [Medication: Code 'L01AX02' from atc] M
    return 'Medication/' + M.id

define "Endokrine TherapieL02Ref":
  from [Medication: Code 'L02' from atc] M
    return 'Medication/' + M.id

define PlitidepsinL01XX57Ref:
  from [Medication: Code 'L01XX57' from atc] M
    return 'Medication/' + M.id

define IxazomibL01XG03Ref:
  from [Medication: Code 'L01XG03' from atc] M
    return 'Medication/' + M.id

define "Andere Hormonantagonisten und verwandte MittelL02BXRef":
  from [Medication: Code 'L02BX' from atc] M
    return 'Medication/' + M.id

define "Pflanzliche MittelL01CPRef":
  from [Medication: Code 'L01CP' from atc] M
    return 'Medication/' + M.id

define OfatumumabL01FA02Ref:
  from [Medication: Code 'L01FA02' from atc] M
    return 'Medication/' + M.id

define "Hepatitis-B-ImmunglobulinJ06BB04Ref":
  from [Medication: Code 'J06BB04' from atc] M
    return 'Medication/' + M.id

define ErlotinibL01EB02Ref:
  from [Medication: Code 'L01EB02' from atc] M
    return 'Medication/' + M.id

define MelphalanL01AA03Ref:
  from [Medication: Code 'L01AA03' from atc] M
    return 'Medication/' + M.id

define LurbinectedinL01XX69Ref:
  from [Medication: Code 'L01XX69' from atc] M
    return 'Medication/' + M.id

define VorinostatL01XH01Ref:
  from [Medication: Code 'L01XH01' from atc] M
    return 'Medication/' + M.id

define NirsevimabJ06BD08Ref:
  from [Medication: Code 'J06BD08' from atc] M
    return 'Medication/' + M.id

define "AminolevulinsäureL01XD04Ref":
  from [Medication: Code 'L01XD04' from atc] M
    return 'Medication/' + M.id

define "Gasbrand-SerumJ06AA05Ref":
  from [Medication: Code 'J06AA05' from atc] M
    return 'Medication/' + M.id

define LevamisolP02CE01Ref:
  from [Medication: Code 'P02CE01' from atc] M
    return 'Medication/' + M.id

define "Organophosphat-VerbindungenP02BBRef":
  from [Medication: Code 'P02BB' from atc] M
    return 'Medication/' + M.id

define MedroxyprogesteronL02AB02Ref:
  from [Medication: Code 'L02AB02' from atc] M
    return 'Medication/' + M.id

define TisagenlecleucelL01XX71Ref:
  from [Medication: Code 'L01XX71' from atc] M
    return 'Medication/' + M.id

define MistelkrautL01CH01Ref:
  from [Medication: Code 'L01CH01' from atc] M
    return 'Medication/' + M.id

define "BRAF-Serin-Threoninkinase-InhibitorenL01ECRef":
  from [Medication: Code 'L01EC' from atc] M
    return 'Medication/' + M.id

define VeliparibL01XK05Ref:
  from [Medication: Code 'L01XK05' from atc] M
    return 'Medication/' + M.id

define ElotuzumabL01FX08Ref:
  from [Medication: Code 'L01FX08' from atc] M
    return 'Medication/' + M.id

define "Retinoide zur KrebsbehandlungL01XFRef":
  from [Medication: Code 'L01XF' from atc] M
    return 'Medication/' + M.id

define PolyplatillenL01XA05Ref:
  from [Medication: Code 'L01XA05' from atc] M
    return 'Medication/' + M.id

define FedratinibL01EJ02Ref:
  from [Medication: Code 'L01EJ02' from atc] M
    return 'Medication/' + M.id

define HistrelinL02AE05Ref:
  from [Medication: Code 'L02AE05' from atc] M
    return 'Medication/' + M.id

define LomustinL01AD02Ref:
  from [Medication: Code 'L01AD02' from atc] M
    return 'Medication/' + M.id

define EdrecolomabL01FX01Ref:
  from [Medication: Code 'L01FX01' from atc] M
    return 'Medication/' + M.id

define CrizotinibL01ED01Ref:
  from [Medication: Code 'L01ED01' from atc] M
    return 'Medication/' + M.id

define SemustinL01AD03Ref:
  from [Medication: Code 'L01AD03' from atc] M
    return 'Medication/' + M.id

define ThiotepaL01AC01Ref:
  from [Medication: Code 'L01AC01' from atc] M
    return 'Medication/' + M.id

define TeniposidL01CB02Ref:
  from [Medication: Code 'L01CB02' from atc] M
    return 'Medication/' + M.id

define BithionolP02BX01Ref:
  from [Medication: Code 'P02BX01' from atc] M
    return 'Medication/' + M.id

define VindesinL01CA03Ref:
  from [Medication: Code 'L01CA03' from atc] M
    return 'Medication/' + M.id

define MitobronitolL01AX01Ref:
  from [Medication: Code 'L01AX01' from atc] M
    return 'Medication/' + M.id

define "Tetanus-AntitoxinJ06AA02Ref":
  from [Medication: Code 'J06AA02' from atc] M
    return 'Medication/' + M.id

define CetuximabL01FE01Ref:
  from [Medication: Code 'L01FE01' from atc] M
    return 'Medication/' + M.id

define TiabendazolP02CA02Ref:
  from [Medication: Code 'P02CA02' from atc] M
    return 'Medication/' + M.id

define "Casirivimab und ImdevimabJ06BD07Ref":
  from [Medication: Code 'J06BD07' from atc] M
    return 'Medication/' + M.id

define OsimertinibL01EB04Ref:
  from [Medication: Code 'L01EB04' from atc] M
    return 'Medication/' + M.id

define FenbendazolP02CA06Ref:
  from [Medication: Code 'P02CA06' from atc] M
    return 'Medication/' + M.id

define "Andere pflanzliche Alkaloide und natürliche MittelL01CXRef":
  from [Medication: Code 'L01CX' from atc] M
    return 'Medication/' + M.id

define PrednimustinL01AA08Ref:
  from [Medication: Code 'L01AA08' from atc] M
    return 'Medication/' + M.id

define "Cytarabin und DaunorubicinL01XY01Ref":
  from [Medication: Code 'L01XY01' from atc] M
    return 'Medication/' + M.id

define BevacizumabL01FG01Ref:
  from [Medication: Code 'L01FG01' from atc] M
    return 'Medication/' + M.id

define IvosidenibL01XX62Ref:
  from [Medication: Code 'L01XX62' from atc] M
    return 'Medication/' + M.id

define "Pertuzumab und TrastuzumabL01XY02Ref":
  from [Medication: Code 'L01XY02' from atc] M
    return 'Medication/' + M.id

define "Gonadotropin-Releasing-Hormon-AnalogaL02AERef":
  from [Medication: Code 'L02AE' from atc] M
    return 'Medication/' + M.id

define LarotrectinibL01EX12Ref:
  from [Medication: Code 'L01EX12' from atc] M
    return 'Medication/' + M.id

define "Andere Nematodenmittel, KombinationenP02CX50Ref":
  from [Medication: Code 'P02CX50' from atc] M
    return 'Medication/' + M.id

define NecitumumabL01FE03Ref:
  from [Medication: Code 'L01FE03' from atc] M
    return 'Medication/' + M.id

define SelumetinibL01EE04Ref:
  from [Medication: Code 'L01EE04' from atc] M
    return 'Medication/' + M.id

define "Oportuzumab monatoxL01FX16Ref":
  from [Medication: Code 'L01FX16' from atc] M
    return 'Medication/' + M.id

define ApalutamidL02BB05Ref:
  from [Medication: Code 'L02BB05' from atc] M
    return 'Medication/' + M.id

define TretinoinL01XF01Ref:
  from [Medication: Code 'L01XF01' from atc] M
    return 'Medication/' + M.id

define "Zytotoxische Antibiotika Und Verwandte SubstanzenL01DRef":
  from [Medication: Code 'L01D' from atc] M
    return 'Medication/' + M.id

define "Andere zytotoxische AntibiotikaL01DCRef":
  from [Medication: Code 'L01DC' from atc] M
    return 'Medication/' + M.id

define LazertinibL01EB09Ref:
  from [Medication: Code 'L01EB09' from atc] M
    return 'Medication/' + M.id

define SitimagenceradenovecL01XX37Ref:
  from [Medication: Code 'L01XX37' from atc] M
    return 'Medication/' + M.id

define "Anaplastische Lymphomkinase (ALK)-InhibitorenL01EDRef":
  from [Medication: Code 'L01ED' from atc] M
    return 'Medication/' + M.id

define CeritinibL01ED02Ref:
  from [Medication: Code 'L01ED02' from atc] M
    return 'Medication/' + M.id

define "Porfimer natriumL01XD01Ref":
  from [Medication: Code 'L01XD01' from atc] M
    return 'Medication/' + M.id

define ClofarabinL01BB06Ref:
  from [Medication: Code 'L01BB06' from atc] M
    return 'Medication/' + M.id

define BinimetinibL01EE03Ref:
  from [Medication: Code 'L01EE03' from atc] M
    return 'Medication/' + M.id

define OmacetaxinmepesuccinatL01XX40Ref:
  from [Medication: Code 'L01XX40' from atc] M
    return 'Medication/' + M.id

define GestonoronL02AB03Ref:
  from [Medication: Code 'L02AB03' from atc] M
    return 'Medication/' + M.id

define DarolutamidL02BB06Ref:
  from [Medication: Code 'L02BB06' from atc] M
    return 'Medication/' + M.id

define CediranibL01EK02Ref:
  from [Medication: Code 'L01EK02' from atc] M
    return 'Medication/' + M.id

define "Schlangengift-AntiserumJ06AA03Ref":
  from [Medication: Code 'J06AA03' from atc] M
    return 'Medication/' + M.id

define CiclobendazolP02CA04Ref:
  from [Medication: Code 'P02CA04' from atc] M
    return 'Medication/' + M.id

define "Belantamab mafodotinL01FX15Ref":
  from [Medication: Code 'L01FX15' from atc] M
    return 'Medication/' + M.id

define MercaptopurinL01BB02Ref:
  from [Medication: Code 'L01BB02' from atc] M
    return 'Medication/' + M.id

define PiperazinP02CB01Ref:
  from [Medication: Code 'P02CB01' from atc] M
    return 'Medication/' + M.id

define CatumaxomabL01FX03Ref:
  from [Medication: Code 'L01FX03' from atc] M
    return 'Medication/' + M.id

define NaxitamabL01FX21Ref:
  from [Medication: Code 'L01FX21' from atc] M
    return 'Medication/' + M.id

define ProcarbazinL01XB01Ref:
  from [Medication: Code 'L01XB01' from atc] M
    return 'Medication/' + M.id

define "Immunglobuline, normal humanJ06BARef":
  from [Medication: Code 'J06BA' from atc] M
    return 'Medication/' + M.id

define "Talimogen laherparepvecL01XX51Ref":
  from [Medication: Code 'L01XX51' from atc] M
    return 'Medication/' + M.id

define DenileukindiftitoxL01XX29Ref:
  from [Medication: Code 'L01XX29' from atc] M
    return 'Medication/' + M.id

define CobimetinibL01EE02Ref:
  from [Medication: Code 'L01EE02' from atc] M
    return 'Medication/' + M.id

define PirarubicinL01DB08Ref:
  from [Medication: Code 'L01DB08' from atc] M
    return 'Medication/' + M.id

define PaclitaxelL01CD01Ref:
  from [Medication: Code 'L01CD01' from atc] M
    return 'Medication/' + M.id

define ImmunseraJ06ARef:
  from [Medication: Code 'J06A' from atc] M
    return 'Medication/' + M.id

define TriclabendazolP02BX04Ref:
  from [Medication: Code 'P02BX04' from atc] M
    return 'Medication/' + M.id

define TremelimumabL01FX20Ref:
  from [Medication: Code 'L01FX20' from atc] M
    return 'Medication/' + M.id

define "EGFR (Epidermaler Wachstumsfaktor-Rezeptor)-InhibitorenL01FERef":
  from [Medication: Code 'L01FE' from atc] M
    return 'Medication/' + M.id

define "Andere Antineoplastische MittelL01XRef":
  from [Medication: Code 'L01X' from atc] M
    return 'Medication/' + M.id

define NimustinL01AD06Ref:
  from [Medication: Code 'L01AD06' from atc] M
    return 'Medication/' + M.id

define MannosulfanL01AB03Ref:
  from [Medication: Code 'L01AB03' from atc] M
    return 'Medication/' + M.id

define TivozanibL01EK03Ref:
  from [Medication: Code 'L01EK03' from atc] M
    return 'Medication/' + M.id

define "Vinka-Alkaloide und AnalogaL01CARef":
  from [Medication: Code 'L01CA' from atc] M
    return 'Medication/' + M.id

define "CD22 (Cluster of Differentiation 22)-InhibitorenL01FBRef":
  from [Medication: Code 'L01FB' from atc] M
    return 'Medication/' + M.id

define AsciminibL01EA06Ref:
  from [Medication: Code 'L01EA06' from atc] M
    return 'Medication/' + M.id

define "Andere alkylierende MittelL01AXRef":
  from [Medication: Code 'L01AX' from atc] M
    return 'Medication/' + M.id

define LeuprorelinL02AE02Ref:
  from [Medication: Code 'L02AE02' from atc] M
    return 'Medication/' + M.id

define "Homöopathische und anthroposophische MittelL01CHRef":
  from [Medication: Code 'L01CH' from atc] M
    return 'Medication/' + M.id

define MebendazolP02CA01Ref:
  from [Medication: Code 'P02CA01' from atc] M
    return 'Medication/' + M.id

define RucaparibL01XK03Ref:
  from [Medication: Code 'L01XK03' from atc] M
    return 'Medication/' + M.id

define ZanubrutinibL01EL03Ref:
  from [Medication: Code 'L01EL03' from atc] M
    return 'Medication/' + M.id

define MethylaminolevulinatL01XD03Ref:
  from [Medication: Code 'L01XD03' from atc] M
    return 'Medication/' + M.id

define OlmutinibL01EB06Ref:
  from [Medication: Code 'L01EB06' from atc] M
    return 'Medication/' + M.id

define "Hedgehog-Signalweg-InhibitorenL01XJRef":
  from [Medication: Code 'L01XJ' from atc] M
    return 'Medication/' + M.id

define CarmustinL01AD01Ref:
  from [Medication: Code 'L01AD01' from atc] M
    return 'Medication/' + M.id

define VandetanibL01EX04Ref:
  from [Medication: Code 'L01EX04' from atc] M
    return 'Medication/' + M.id

define AlbendazolP02CA03Ref:
  from [Medication: Code 'P02CA03' from atc] M
    return 'Medication/' + M.id

define "Sensibilisatoren für die photodynamische/Radio-TherapieL01XDRef":
  from [Medication: Code 'L01XD' from atc] M
    return 'Medication/' + M.id

define MoxidectinP02CX03Ref:
  from [Medication: Code 'P02CX03' from atc] M
    return 'Medication/' + M.id

define OxantelP02CC02Ref:
  from [Medication: Code 'P02CC02' from atc] M
    return 'Medication/' + M.id

define NelarabinL01BB07Ref:
  from [Medication: Code 'L01BB07' from atc] M
    return 'Medication/' + M.id

define EstramustinL01XX11Ref:
  from [Medication: Code 'L01XX11' from atc] M
    return 'Medication/' + M.id

define DacarbazinL01AX04Ref:
  from [Medication: Code 'L01AX04' from atc] M
    return 'Medication/' + M.id

define FosfestrolL02AA04Ref:
  from [Medication: Code 'L02AA04' from atc] M
    return 'Medication/' + M.id

define GoserelinL02AE03Ref:
  from [Medication: Code 'L02AE03' from atc] M
    return 'Medication/' + M.id

define TucatinibL01EH03Ref:
  from [Medication: Code 'L01EH03' from atc] M
    return 'Medication/' + M.id

define "Kombinationen von antineoplastischen MittelnL01XYRef":
  from [Medication: Code 'L01XY' from atc] M
    return 'Medication/' + M.id

define "Andere NematodenmittelP02CXRef":
  from [Medication: Code 'P02CX' from atc] M
    return 'Medication/' + M.id

define PonatinibL01EA05Ref:
  from [Medication: Code 'L01EA05' from atc] M
    return 'Medication/' + M.id

define EtoglucidL01AG01Ref:
  from [Medication: Code 'L01AG01' from atc] M
    return 'Medication/' + M.id

define LapatinibL01EH01Ref:
  from [Medication: Code 'L01EH01' from atc] M
    return 'Medication/' + M.id

define ImmunseraJ06AARef:
  from [Medication: Code 'J06AA' from atc] M
    return 'Medication/' + M.id

define "Andere antineoplastische MittelL01XXRef":
  from [Medication: Code 'L01XX' from atc] M
    return 'Medication/' + M.id

define VenetoclaxL01XX52Ref:
  from [Medication: Code 'L01XX52' from atc] M
    return 'Medication/' + M.id

define "Masern-ImmunglobulinJ06BB14Ref":
  from [Medication: Code 'J06BB14' from atc] M
    return 'Medication/' + M.id

define AlitretinoinL01XF02Ref:
  from [Medication: Code 'L01XF02' from atc] M
    return 'Medication/' + M.id

define BicalutamidL02BB03Ref:
  from [Medication: Code 'L02BB03' from atc] M
    return 'Medication/' + M.id

define NeratinibL01EH02Ref:
  from [Medication: Code 'L01EH02' from atc] M
    return 'Medication/' + M.id

define RomidepsinL01XH02Ref:
  from [Medication: Code 'L01XH02' from atc] M
    return 'Medication/' + M.id

define LorlatinibL01ED05Ref:
  from [Medication: Code 'L01ED05' from atc] M
    return 'Medication/' + M.id

define DesaspidinP02DX01Ref:
  from [Medication: Code 'P02DX01' from atc] M
    return 'Medication/' + M.id

define "Stickstofflost-AnalogaL01AARef":
  from [Medication: Code 'L01AA' from atc] M
    return 'Medication/' + M.id

define EpoxideL01AGRef:
  from [Medication: Code 'L01AG' from atc] M
    return 'Medication/' + M.id

define SunitinibL01EX01Ref:
  from [Medication: Code 'L01EX01' from atc] M
    return 'Medication/' + M.id

define OlaratumabL01FX10Ref:
  from [Medication: Code 'L01FX10' from atc] M
    return 'Medication/' + M.id

define "Monoklonale Antikörper Und Antikörper-Wirkstoff-KonjugateL01FRef":
  from [Medication: Code 'L01F' from atc] M
    return 'Medication/' + M.id

define RipretinibL01EX19Ref:
  from [Medication: Code 'L01EX19' from atc] M
    return 'Medication/' + M.id

define CisplatinL01XA01Ref:
  from [Medication: Code 'L01XA01' from atc] M
    return 'Medication/' + M.id

define AclarubicinL01DB04Ref:
  from [Medication: Code 'L01DB04' from atc] M
    return 'Medication/' + M.id

define AlectinibL01ED03Ref:
  from [Medication: Code 'L01ED03' from atc] M
    return 'Medication/' + M.id

define StreptozocinL01AD04Ref:
  from [Medication: Code 'L01AD04' from atc] M
    return 'Medication/' + M.id

define FloxuridinL01BC09Ref:
  from [Medication: Code 'L01BC09' from atc] M
    return 'Medication/' + M.id

define EribulinL01XX41Ref:
  from [Medication: Code 'L01XX41' from atc] M
    return 'Medication/' + M.id

define "Aromatase-InhibitorenL02BGRef":
  from [Medication: Code 'L02BG' from atc] M
    return 'Medication/' + M.id

define "Varicella/Zoster-ImmunglobulinJ06BB03Ref":
  from [Medication: Code 'J06BB03' from atc] M
    return 'Medication/' + M.id

define "Poly-ADP-Ribose-Polymerase (PARP)-InhibitorenL01XKRef":
  from [Medication: Code 'L01XK' from atc] M
    return 'Medication/' + M.id

define AntiandrogeneL02BBRef:
  from [Medication: Code 'L02BB' from atc] M
    return 'Medication/' + M.id

define OlaparibL01XK01Ref:
  from [Medication: Code 'L01XK01' from atc] M
    return 'Medication/' + M.id

define ObiltoxaximabJ06BC04Ref:
  from [Medication: Code 'J06BC04' from atc] M
    return 'Medication/' + M.id

define CapecitabinL01BC06Ref:
  from [Medication: Code 'L01BC06' from atc] M
    return 'Medication/' + M.id

define VinblastinL01CA01Ref:
  from [Medication: Code 'L01CA01' from atc] M
    return 'Medication/' + M.id

define AntimetabolitenL01BRef:
  from [Medication: Code 'L01B' from atc] M
    return 'Medication/' + M.id

define "HER2 (Humaner epidermaler Wachstumsfaktor-Rezeptor 2)-InhibitorenL01FDRef":
  from [Medication: Code 'L01FD' from atc] M
    return 'Medication/' + M.id

define TislelizumabL01FF09Ref:
  from [Medication: Code 'L01FF09' from atc] M
    return 'Medication/' + M.id

define EntrectinibL01EX14Ref:
  from [Medication: Code 'L01EX14' from atc] M
    return 'Medication/' + M.id

define "Pertussis-ImmunglobulinJ06BB13Ref":
  from [Medication: Code 'J06BB13' from atc] M
    return 'Medication/' + M.id

define BendamustinL01AA09Ref:
  from [Medication: Code 'L01AA09' from atc] M
    return 'Medication/' + M.id

define PraziquantelP02BA01Ref:
  from [Medication: Code 'P02BA01' from atc] M
    return 'Medication/' + M.id

define MidostaurinL01EX10Ref:
  from [Medication: Code 'L01EX10' from atc] M
    return 'Medication/' + M.id

define PemigatinibL01EN02Ref:
  from [Medication: Code 'L01EN02' from atc] M
    return 'Medication/' + M.id

define NebacumabJ06BC01Ref:
  from [Medication: Code 'J06BC01' from atc] M
    return 'Medication/' + M.id

define BepheniumP02CX02Ref:
  from [Medication: Code 'P02CX02' from atc] M
    return 'Medication/' + M.id

define ImmunglobulineJ06BRef:
  from [Medication: Code 'J06B' from atc] M
    return 'Medication/' + M.id

define MasoprocolL01XX10Ref:
  from [Medication: Code 'L01XX10' from atc] M
    return 'Medication/' + M.id

define "Autologe T-Zellen, genetisch modifiziertL01XX91Ref":
  from [Medication: Code 'L01XX91' from atc] M
    return 'Medication/' + M.id

define BusulfanL01AB01Ref:
  from [Medication: Code 'L01AB01' from atc] M
    return 'Medication/' + M.id

define AnastrozolL02BG03Ref:
  from [Medication: Code 'L02BG03' from atc] M
    return 'Medication/' + M.id

define AfatinibL01EB03Ref:
  from [Medication: Code 'L01EB03' from atc] M
    return 'Medication/' + M.id

define RaltitrexedL01BA03Ref:
  from [Medication: Code 'L01BA03' from atc] M
    return 'Medication/' + M.id

define TioguaninL01BB03Ref:
  from [Medication: Code 'L01BB03' from atc] M
    return 'Medication/' + M.id

define AminoglutethimidL02BG01Ref:
  from [Medication: Code 'L02BG01' from atc] M
    return 'Medication/' + M.id

define "Etirinotecan pegolL01CE03Ref":
  from [Medication: Code 'L01CE03' from atc] M
    return 'Medication/' + M.id

define DaunorubicinL01DB02Ref:
  from [Medication: Code 'L01DB02' from atc] M
    return 'Medication/' + M.id

define CelecoxibL01XX33Ref:
  from [Medication: Code 'L01XX33' from atc] M
    return 'Medication/' + M.id

define MethylhydrazineL01XBRef:
  from [Medication: Code 'L01XB' from atc] M
    return 'Medication/' + M.id

define FlubendazolP02CA05Ref:
  from [Medication: Code 'P02CA05' from atc] M
    return 'Medication/' + M.id

define AcalabrutinibL01EL02Ref:
  from [Medication: Code 'L01EL02' from atc] M
    return 'Medication/' + M.id

define HydroxycarbamidL01XX05Ref:
  from [Medication: Code 'L01XX05' from atc] M
    return 'Medication/' + M.id

define "Antivirale monoklonale AntikörperJ06BDRef":
  from [Medication: Code 'J06BD' from atc] M
    return 'Medication/' + M.id

define "Tixagevimab und CilgavimabJ06BD03Ref":
  from [Medication: Code 'J06BD03' from atc] M
    return 'Medication/' + M.id

define RegdanvimabJ06BD06Ref:
  from [Medication: Code 'J06BD06' from atc] M
    return 'Medication/' + M.id

define RuxolitinibL01EJ01Ref:
  from [Medication: Code 'L01EJ01' from atc] M
    return 'Medication/' + M.id

define MelphalanflufenamidL01AA10Ref:
  from [Medication: Code 'L01AA10' from atc] M
    return 'Medication/' + M.id

define AlkylsulfonateL01ABRef:
  from [Medication: Code 'L01AB' from atc] M
    return 'Medication/' + M.id

define "Podophyllotoxin-DerivateL01CBRef":
  from [Medication: Code 'L01CB' from atc] M
    return 'Medication/' + M.id

define RanimustinL01AD07Ref:
  from [Medication: Code 'L01AD07' from atc] M
    return 'Medication/' + M.id

define "Hepatitis-A-ImmunglobulinJ06BB11Ref":
  from [Medication: Code 'J06BB11' from atc] M
    return 'Medication/' + M.id

define GilteritinibL01EX13Ref:
  from [Medication: Code 'L01EX13' from atc] M
    return 'Medication/' + M.id

define TazemetostatL01XX72Ref:
  from [Medication: Code 'L01XX72' from atc] M
    return 'Medication/' + M.id

define "Purin-AnalogaL01BBRef":
  from [Medication: Code 'L01BB' from atc] M
    return 'Medication/' + M.id

define FormestanL02BG02Ref:
  from [Medication: Code 'L02BG02' from atc] M
    return 'Medication/' + M.id

define MotavizumabJ06BD02Ref:
  from [Medication: Code 'J06BD02' from atc] M
    return 'Medication/' + M.id

define EthinylestradiolL02AA03Ref:
  from [Medication: Code 'L02AA03' from atc] M
    return 'Medication/' + M.id

define "Andere Proteinkinase-InhibitorenL01EXRef":
  from [Medication: Code 'L01EX' from atc] M
    return 'Medication/' + M.id

define BosutinibL01EA04Ref:
  from [Medication: Code 'L01EA04' from atc] M
    return 'Medication/' + M.id

define PentostatinL01XX08Ref:
  from [Medication: Code 'L01XX08' from atc] M
    return 'Medication/' + M.id

define "Trastuzumab duocarmazinL01FD05Ref":
  from [Medication: Code 'L01FD05' from atc] M
    return 'Medication/' + M.id

define IpilimumabL01FX04Ref:
  from [Medication: Code 'L01FX04' from atc] M
    return 'Medication/' + M.id

define TriptorelinL02AE04Ref:
  from [Medication: Code 'L02AE04' from atc] M
    return 'Medication/' + M.id

define IvermectinP02CF01Ref:
  from [Medication: Code 'P02CF01' from atc] M
    return 'Medication/' + M.id

define ChlormethinL01AA05Ref:
  from [Medication: Code 'L01AA05' from atc] M
    return 'Medication/' + M.id

define PaclitaxelpoliglumexL01CD03Ref:
  from [Medication: Code 'L01CD03' from atc] M
    return 'Medication/' + M.id

define MargetuximabL01FD06Ref:
  from [Medication: Code 'L01FD06' from atc] M
    return 'Medication/' + M.id

define "Mitogen-aktivierte Proteinkinase (MEK)-InhibitorenL01EERef":
  from [Medication: Code 'L01EE' from atc] M
    return 'Medication/' + M.id

define "Piperazin und DerivateP02CBRef":
  from [Medication: Code 'P02CB' from atc] M
    return 'Medication/' + M.id

define "Polatuzumab vedotinL01FX14Ref":
  from [Medication: Code 'L01FX14' from atc] M
    return 'Medication/' + M.id

define QuizartinibL01EX11Ref:
  from [Medication: Code 'L01EX11' from atc] M
    return 'Medication/' + M.id

define ExemestanL02BG06Ref:
  from [Medication: Code 'L02BG06' from atc] M
    return 'Medication/' + M.id

define DoxorubicinL01DB01Ref:
  from [Medication: Code 'L01DB01' from atc] M
    return 'Medication/' + M.id

define "Inotuzumab ozogamicinL01FB01Ref":
  from [Medication: Code 'L01FB01' from atc] M
    return 'Medication/' + M.id

define DaratumumabL01FC01Ref:
  from [Medication: Code 'L01FC01' from atc] M
    return 'Medication/' + M.id

define KombinationenJ06BB30Ref:
  from [Medication: Code 'J06BB30' from atc] M
    return 'Medication/' + M.id

define BleomycinL01DC01Ref:
  from [Medication: Code 'L01DC01' from atc] M
    return 'Medication/' + M.id

define GestageneL02ABRef:
  from [Medication: Code 'L02AB' from atc] M
    return 'Medication/' + M.id

define "BCR-ABL-Tyrosinkinase-InhibitorenL01EARef":
  from [Medication: Code 'L01EA' from atc] M
    return 'Medication/' + M.id

define "Humaner epidermaler Wachstumsfaktor-Rezeptor 2 (HER2)-Tyrosinkinase-InhibitorenL01EHRef":
  from [Medication: Code 'L01EH' from atc] M
    return 'Medication/' + M.id

define BuserelinL02AE01Ref:
  from [Medication: Code 'L02AE01' from atc] M
    return 'Medication/' + M.id

define ImatinibL01EA01Ref:
  from [Medication: Code 'L01EA01' from atc] M
    return 'Medication/' + M.id

define LetrozolL02BG04Ref:
  from [Medication: Code 'L02BG04' from atc] M
    return 'Medication/' + M.id

define "CD20 (Cluster of Differentiation 20)-InhibitorenL01FARef":
  from [Medication: Code 'L01FA' from atc] M
    return 'Medication/' + M.id

define FludarabinL01BB05Ref:
  from [Medication: Code 'L01BB05' from atc] M
    return 'Medication/' + M.id

define RamucirumabL01FG02Ref:
  from [Medication: Code 'L01FG02' from atc] M
    return 'Medication/' + M.id

define GefitinibL01EB01Ref:
  from [Medication: Code 'L01EB01' from atc] M
    return 'Medication/' + M.id

define "Tegafur und UracilL01BC63Ref":
  from [Medication: Code 'L01BC63' from atc] M
    return 'Medication/' + M.id

define BandwurmmittelP02DRef:
  from [Medication: Code 'P02D' from atc] M
    return 'Medication/' + M.id

define SotorasibL01XX73Ref:
  from [Medication: Code 'L01XX73' from atc] M
    return 'Medication/' + M.id

define SurufatinibL01EX24Ref:
  from [Medication: Code 'L01EX24' from atc] M
    return 'Medication/' + M.id

define SabatolimabL01FX19Ref:
  from [Medication: Code 'L01FX19' from atc] M
    return 'Medication/' + M.id

define "Vaskulärer endothelialer Wachstumsfaktor-Rezeptor (VEGFR)-Tyrosinkinase-InhibitorenL01EKRef":
  from [Medication: Code 'L01EK' from atc] M
    return 'Medication/' + M.id

define "Pyrimidin-AnalogaL01BCRef":
  from [Medication: Code 'L01BC' from atc] M
    return 'Medication/' + M.id

define "Cyclin-abhängige Kinasen (CDK)-InhibitorenL01EFRef":
  from [Medication: Code 'L01EF' from atc] M
    return 'Medication/' + M.id

define IbrutinibL01EL01Ref:
  from [Medication: Code 'L01EL01' from atc] M
    return 'Medication/' + M.id

define CarboquonL01AC03Ref:
  from [Medication: Code 'L01AC03' from atc] M
    return 'Medication/' + M.id

define "Folsäure-AnalogaL01BARef":
  from [Medication: Code 'L01BA' from atc] M
    return 'Medication/' + M.id

define CarfilzomibL01XG02Ref:
  from [Medication: Code 'L01XG02' from atc] M
    return 'Medication/' + M.id

define MitoxantronL01DB07Ref:
  from [Medication: Code 'L01DB07' from atc] M
    return 'Medication/' + M.id

define AbirateronL02BX03Ref:
  from [Medication: Code 'L02BX03' from atc] M
    return 'Medication/' + M.id

define TopotecanL01CE01Ref:
  from [Medication: Code 'L01CE01' from atc] M
    return 'Medication/' + M.id

define "Mammalian target of rapamycin (mTOR)-Kinase-InhibitorenL01EGRef":
  from [Medication: Code 'L01EG' from atc] M
    return 'Medication/' + M.id

define FotemustinL01AD05Ref:
  from [Medication: Code 'L01AD05' from atc] M
    return 'Medication/' + M.id

define DiethylstilbestrolL02AA01Ref:
  from [Medication: Code 'L02AA01' from atc] M
    return 'Medication/' + M.id

define "Gemtuzumab ozogamicinL01FX02Ref":
  from [Medication: Code 'L01FX02' from atc] M
    return 'Medication/' + M.id

define IfosfamidL01AA06Ref:
  from [Medication: Code 'L01AA06' from atc] M
    return 'Medication/' + M.id

define VincristinL01CA02Ref:
  from [Medication: Code 'L01CA02' from atc] M
    return 'Medication/' + M.id

define "Anti-D(rh)-ImmunglobulinJ06BB01Ref":
  from [Medication: Code 'J06BB01' from atc] M
    return 'Medication/' + M.id

define TrabectedinL01CX01Ref:
  from [Medication: Code 'L01CX01' from atc] M
    return 'Medication/' + M.id

define VintafolidL01CA06Ref:
  from [Medication: Code 'L01CA06' from atc] M
    return 'Medication/' + M.id

define "Diphtherie-AntitoxinJ06AA01Ref":
  from [Medication: Code 'J06AA01' from atc] M
    return 'Medication/' + M.id

define NitrosoharnstoffeL01ADRef:
  from [Medication: Code 'L01AD' from atc] M
    return 'Medication/' + M.id

define ActinomycineL01DARef:
  from [Medication: Code 'L01DA' from atc] M
    return 'Medication/' + M.id

define GemcitabinL01BC05Ref:
  from [Medication: Code 'L01BC05' from atc] M
    return 'Medication/' + M.id

define "Antineoplastische MittelL01Ref":
  from [Medication: Code 'L01' from atc] M
    return 'Medication/' + M.id

define PlicamycinL01DC02Ref:
  from [Medication: Code 'L01DC02' from atc] M
    return 'Medication/' + M.id

define BrigatinibL01ED04Ref:
  from [Medication: Code 'L01ED04' from atc] M
    return 'Medication/' + M.id

define PegaspargaseL01XX24Ref:
  from [Medication: Code 'L01XX24' from atc] M
    return 'Medication/' + M.id

define TriaziquonL01AC02Ref:
  from [Medication: Code 'L01AC02' from atc] M
    return 'Medication/' + M.id

define "Tetanus-ImmunglobulinJ06BB02Ref":
  from [Medication: Code 'J06BB02' from atc] M
    return 'Medication/' + M.id

define VosaroxinL01XX53Ref:
  from [Medication: Code 'L01XX53' from atc] M
    return 'Medication/' + M.id

define "Botulismus-AntitoxinJ06AA04Ref":
  from [Medication: Code 'J06AA04' from atc] M
    return 'Medication/' + M.id

define AmivantamabL01FX18Ref:
  from [Medication: Code 'L01FX18' from atc] M
    return 'Medication/' + M.id

define NilutamidL02BB02Ref:
  from [Medication: Code 'L02BB02' from atc] M
    return 'Medication/' + M.id

define "Fibroblasten-Wachstumsfaktor-Rezeptor (FGFR)-Tyrosinkinase-InhibitorenL01ENRef":
  from [Medication: Code 'L01EN' from atc] M
    return 'Medication/' + M.id

define "Idecabtagen vicleucelL01XX77Ref":
  from [Medication: Code 'L01XX77' from atc] M
    return 'Medication/' + M.id

define ArsentrioxidL01XX27Ref:
  from [Medication: Code 'L01XX27' from atc] M
    return 'Medication/' + M.id

define VemurafenibL01EC01Ref:
  from [Medication: Code 'L01EC01' from atc] M
    return 'Medication/' + M.id

define "Spezifische ImmunglobulineJ06BBRef":
  from [Medication: Code 'J06BB' from atc] M
    return 'Medication/' + M.id

define "Dinutuximab betaL01FX06Ref":
  from [Medication: Code 'L01FX06' from atc] M
    return 'Medication/' + M.id

define EpacadostatL01XX58Ref:
  from [Medication: Code 'L01XX58' from atc] M
    return 'Medication/' + M.id

define DecitabinL01BC08Ref:
  from [Medication: Code 'L01BC08' from atc] M
    return 'Medication/' + M.id

define PazopanibL01EX03Ref:
  from [Medication: Code 'L01EX03' from atc] M
    return 'Medication/' + M.id

define LenvatinibL01EX08Ref:
  from [Medication: Code 'L01EX08' from atc] M
    return 'Medication/' + M.id

define RegorafenibL01EX05Ref:
  from [Medication: Code 'L01EX05' from atc] M
    return 'Medication/' + M.id

define OxamniquinP02BA02Ref:
  from [Medication: Code 'P02BA02' from atc] M
    return 'Medication/' + M.id

define RelugolixL02BX04Ref:
  from [Medication: Code 'L02BX04' from atc] M
    return 'Medication/' + M.id

define "Antibakterielle monoklonale AntikörperJ06BCRef":
  from [Medication: Code 'J06BC' from atc] M
    return 'Medication/' + M.id

define AnagrelidL01XX35Ref:
  from [Medication: Code 'L01XX35' from atc] M
    return 'Medication/' + M.id

define PalivizumabJ06BD01Ref:
  from [Medication: Code 'J06BD01' from atc] M
    return 'Medication/' + M.id

define PyrviniumP02CX01Ref:
  from [Medication: Code 'P02CX01' from atc] M
    return 'Medication/' + M.id

define "Topoisomerase-1 (TOP-1)-InhibitorenL01CERef":
  from [Medication: Code 'L01CE' from atc] M
    return 'Medication/' + M.id

define RituximabL01FA01Ref:
  from [Medication: Code 'L01FA01' from atc] M
    return 'Medication/' + M.id

define DostarlimabL01FF07Ref:
  from [Medication: Code 'L01FF07' from atc] M
    return 'Medication/' + M.id

define NiridazolP02BX02Ref:
  from [Medication: Code 'P02BX02' from atc] M
    return 'Medication/' + M.id

define "Salicylsäure-DerivateP02DARef":
  from [Medication: Code 'P02DA' from atc] M
    return 'Medication/' + M.id

define "Immunglobuline, normal human, zur extravasalen AnwendungJ06BA01Ref":
  from [Medication: Code 'J06BA01' from atc] M
    return 'Medication/' + M.id

define AzacitidinL01BC07Ref:
  from [Medication: Code 'L01BC07' from atc] M
    return 'Medication/' + M.id

define "Trifluridin, KombinationenL01BC59Ref":
  from [Medication: Code 'L01BC59' from atc] M
    return 'Medication/' + M.id

define "Epidermaler Wachstumsfaktor-Rezeptor (EGFR)-Tyrosinkinase-InhibitorenL01EBRef":
  from [Medication: Code 'L01EB' from atc] M
    return 'Medication/' + M.id

define OxaliplatinL01XA03Ref:
  from [Medication: Code 'L01XA03' from atc] M
    return 'Medication/' + M.id

define CarboplatinL01XA02Ref:
  from [Medication: Code 'L01XA02' from atc] M
    return 'Medication/' + M.id

define IxabepilonL01DC04Ref:
  from [Medication: Code 'L01DC04' from atc] M
    return 'Medication/' + M.id

define NilotinibL01EA03Ref:
  from [Medication: Code 'L01EA03' from atc] M
    return 'Medication/' + M.id

define MasitinibL01EX06Ref:
  from [Medication: Code 'L01EX06' from atc] M
    return 'Medication/' + M.id

define AxitinibL01EK01Ref:
  from [Medication: Code 'L01EK01' from atc] M
    return 'Medication/' + M.id

define FulvestrantL02BA03Ref:
  from [Medication: Code 'L02BA03' from atc] M
    return 'Medication/' + M.id

define RociletinibL01EB05Ref:
  from [Medication: Code 'L01EB05' from atc] M
    return 'Medication/' + M.id

define MethotrexatL01BA01Ref:
  from [Medication: Code 'L01BA01' from atc] M
    return 'Medication/' + M.id

define BlinatumomabL01FX07Ref:
  from [Medication: Code 'L01FX07' from atc] M
    return 'Medication/' + M.id

define "Imidazothiazol-DerivateP02CERef":
  from [Medication: Code 'P02CE' from atc] M
    return 'Medication/' + M.id

define "Paclitaxel und EncequidarL01CD51Ref":
  from [Medication: Code 'L01CD51' from atc] M
    return 'Medication/' + M.id

define AsparaginaseL01XX02Ref:
  from [Medication: Code 'L01XX02' from atc] M
    return 'Medication/' + M.id

define "Anthrax-ImmunglobulinJ06BB19Ref":
  from [Medication: Code 'J06BB19' from atc] M
    return 'Medication/' + M.id

define "FSME-ImmunglobulinJ06BB12Ref":
  from [Medication: Code 'J06BB12' from atc] M
    return 'Medication/' + M.id

define "Mebendazol, KombinationenP02CA51Ref":
  from [Medication: Code 'P02CA51' from atc] M
    return 'Medication/' + M.id

define TaxaneL01CDRef:
  from [Medication: Code 'L01CD' from atc] M
    return 'Medication/' + M.id

define "Andere TrematodenmittelP02BXRef":
  from [Medication: Code 'P02BX' from atc] M
    return 'Medication/' + M.id

define IdarubicinL01DB06Ref:
  from [Medication: Code 'L01DB06' from atc] M
    return 'Medication/' + M.id

define "Andere HormoneL02AXRef":
  from [Medication: Code 'L02AX' from atc] M
    return 'Medication/' + M.id

define "Sacituzumab govitecanL01FX17Ref":
  from [Medication: Code 'L01FX17' from atc] M
    return 'Medication/' + M.id

define AmsacrinL01XX01Ref:
  from [Medication: Code 'L01XX01' from atc] M
    return 'Medication/' + M.id

define VismodegibL01XJ01Ref:
  from [Medication: Code 'L01XJ01' from atc] M
    return 'Medication/' + M.id

define EncorafenibL01EC03Ref:
  from [Medication: Code 'L01EC03' from atc] M
    return 'Medication/' + M.id

define "Trastuzumab deruxtecanL01FD04Ref":
  from [Medication: Code 'L01FD04' from atc] M
    return 'Medication/' + M.id

define "Tollwut-SerumJ06AA06Ref":
  from [Medication: Code 'J06AA06' from atc] M
    return 'Medication/' + M.id

define SotrovimabJ06BD05Ref:
  from [Medication: Code 'J06BD05' from atc] M
    return 'Medication/' + M.id

define EthylenimineL01ACRef:
  from [Medication: Code 'L01AC' from atc] M
    return 'Medication/' + M.id

define "Andere pflanzliche Mittel, KombinationenL01CP50Ref":
  from [Medication: Code 'L01CP50' from atc] M
    return 'Medication/' + M.id

define NematodenmittelP02CRef:
  from [Medication: Code 'P02C' from atc] M
    return 'Medication/' + M.id

define StibophenP02BX03Ref:
  from [Medication: Code 'P02BX03' from atc] M
    return 'Medication/' + M.id

define NiclosamidP02DA01Ref:
  from [Medication: Code 'P02DA01' from atc] M
    return 'Medication/' + M.id

define NiraparibL01XK02Ref:
  from [Medication: Code 'L01XK02' from atc] M
    return 'Medication/' + M.id

define InfigratinibL01EN03Ref:
  from [Medication: Code 'L01EN03' from atc] M
    return 'Medication/' + M.id

define EverolimusL01EG02Ref:
  from [Medication: Code 'L01EG02' from atc] M
    return 'Medication/' + M.id

define RetifanlimabL01FF10Ref:
  from [Medication: Code 'L01FF10' from atc] M
    return 'Medication/' + M.id

define RaxibacumabJ06BC02Ref:
  from [Medication: Code 'J06BC02' from atc] M
    return 'Medication/' + M.id

define "Andere monoklonale Antikörper und Antikörper-Wirkstoff-KonjugateL01FXRef":
  from [Medication: Code 'L01FX' from atc] M
    return 'Medication/' + M.id

define RibociclibL01EF02Ref:
  from [Medication: Code 'L01EF02' from atc] M
    return 'Medication/' + M.id

define DactinomycinL01DA01Ref:
  from [Medication: Code 'L01DA01' from atc] M
    return 'Medication/' + M.id

define TemozolomidL01AX03Ref:
  from [Medication: Code 'L01AX03' from atc] M
    return 'Medication/' + M.id

define "Phosphatidylinositol-3-kinase (Pi3K)-InhibitorenL01EMRef":
  from [Medication: Code 'L01EM' from atc] M
    return 'Medication/' + M.id

define TemsirolimusL01EG01Ref:
  from [Medication: Code 'L01EG01' from atc] M
    return 'Medication/' + M.id

define MitomycinL01DC03Ref:
  from [Medication: Code 'L01DC03' from atc] M
    return 'Medication/' + M.id

define BelzutifanL01XX74Ref:
  from [Medication: Code 'L01XX74' from atc] M
    return 'Medication/' + M.id

define ErdafitinibL01EN01Ref:
  from [Medication: Code 'L01EN01' from atc] M
    return 'Medication/' + M.id

define ValrubicinL01DB09Ref:
  from [Medication: Code 'L01DB09' from atc] M
    return 'Medication/' + M.id

define ObinutuzumabL01FA03Ref:
  from [Medication: Code 'L01FA03' from atc] M
    return 'Medication/' + M.id

define FluorouracilL01BC02Ref:
  from [Medication: Code 'L01BC02' from atc] M
    return 'Medication/' + M.id

define BelotecanL01CE04Ref:
  from [Medication: Code 'L01CE04' from atc] M
    return 'Medication/' + M.id

define "Immunglobuline, normal human, zur intravasalen AnwendungJ06BA02Ref":
  from [Medication: Code 'J06BA02' from atc] M
    return 'Medication/' + M.id

define DocetaxelL01CD02Ref:
  from [Medication: Code 'L01CD02' from atc] M
    return 'Medication/' + M.id

define TiazofurinL01XX18Ref:
  from [Medication: Code 'L01XX18' from atc] M
    return 'Medication/' + M.id

define AbemaciclibL01EF03Ref:
  from [Medication: Code 'L01EF03' from atc] M
    return 'Medication/' + M.id

define ZorubicinL01DB05Ref:
  from [Medication: Code 'L01DB05' from atc] M
    return 'Medication/' + M.id

define VenusfliegenfalleL01CP02Ref:
  from [Medication: Code 'L01CP02' from atc] M
    return 'Medication/' + M.id

define DasatinibL01EA02Ref:
  from [Medication: Code 'L01EA02' from atc] M
    return 'Medication/' + M.id

define PexidartinibL01EX15Ref:
  from [Medication: Code 'L01EX15' from atc] M
    return 'Medication/' + M.id

define "Brentuximab vedotinL01FX05Ref":
  from [Medication: Code 'L01FX05' from atc] M
    return 'Medication/' + M.id

define CarmofurL01BC04Ref:
  from [Medication: Code 'L01BC04' from atc] M
    return 'Medication/' + M.id

define SelinexorL01XX66Ref:
  from [Medication: Code 'L01XX66' from atc] M
    return 'Medication/' + M.id

define "Leuprorelin und BicalutamidL02AE51Ref":
  from [Medication: Code 'L02AE51' from atc] M
    return 'Medication/' + M.id

define "Ciltacabtagen autoleucelL01XX76Ref":
  from [Medication: Code 'L01XX76' from atc] M
    return 'Medication/' + M.id

define "Staphylococcus-ImmunglobulinJ06BB08Ref":
  from [Medication: Code 'J06BB08' from atc] M
    return 'Medication/' + M.id

define TrematodenmittelP02BRef:
  from [Medication: Code 'P02B' from atc] M
    return 'Medication/' + M.id

define IcotinibL01EB08Ref:
  from [Medication: Code 'L01EB08' from atc] M
    return 'Medication/' + M.id

define UramustinL01AD08Ref:
  from [Medication: Code 'L01AD08' from atc] M
    return 'Medication/' + M.id

define AlpelisibL01EM03Ref:
  from [Medication: Code 'L01EM03' from atc] M
    return 'Medication/' + M.id

define FutibatinibL01EN04Ref:
  from [Medication: Code 'L01EN04' from atc] M
    return 'Medication/' + M.id

define "Bruton-Tyrosinkinase (BTK)-InhibitorenL01ELRef":
  from [Medication: Code 'L01EL' from atc] M
    return 'Medication/' + M.id

define "Colchicin-DerivateL01CCRef":
  from [Medication: Code 'L01CC' from atc] M
    return 'Medication/' + M.id

define AltretaminL01XX03Ref:
  from [Medication: Code 'L01XX03' from atc] M
    return 'Medication/' + M.id

define "PD-1/PDL-1 (Programmed Cell Death-1-Rezeptor/Programmed Cell Death-Ligand-1)-InhibitorenL01FFRef":
  from [Medication: Code 'L01FF' from atc] M
    return 'Medication/' + M.id

define "Cytomegalievirus-ImmunglobulinJ06BB09Ref":
  from [Medication: Code 'J06BB09' from atc] M
    return 'Medication/' + M.id

define TreosulfanL01AB02Ref:
  from [Medication: Code 'L01AB02' from atc] M
    return 'Medication/' + M.id

define OblimersenL01XX36Ref:
  from [Medication: Code 'L01XX36' from atc] M
    return 'Medication/' + M.id

define PralsetinibL01EX23Ref:
  from [Medication: Code 'L01EX23' from atc] M
    return 'Medication/' + M.id

define RidaforolimusL01EG03Ref:
  from [Medication: Code 'L01EG03' from atc] M
    return 'Medication/' + M.id

define "Chinolin-Derivate und verwandte SubstanzenP02BARef":
  from [Medication: Code 'P02BA' from atc] M
    return 'Medication/' + M.id

define AfliberceptL01XX44Ref:
  from [Medication: Code 'L01XX44' from atc] M
    return 'Medication/' + M.id

define DomiodolR05CB08Ref:
  from [Medication: Code 'R05CB08' from atc] M
    return 'Medication/' + M.id

define "Dextromethorphan, KombinationenR05DA59Ref":
  from [Medication: Code 'R05DA59' from atc] M
    return 'Medication/' + M.id

define "Andere Zubereitungen Gegen ErkältungskrankheitenR05XRef":
  from [Medication: Code 'R05X' from atc] M
    return 'Medication/' + M.id

define "Ivacaftor und LumacaftorR07AX30Ref":
  from [Medication: Code 'R07AX30' from atc] M
    return 'Medication/' + M.id

define "Andere pflanzliche Mittel gegen ErkältungskrankheitenR05XPRef":
  from [Medication: Code 'R05XP' from atc] M
    return 'Medication/' + M.id

define "Salicylamid, KombinationenR05XA09Ref":
  from [Medication: Code 'R05XA09' from atc] M
    return 'Medication/' + M.id

define HydrocodonR05DA03Ref:
  from [Medication: Code 'R05DA03' from atc] M
    return 'Medication/' + M.id

define "Sulfadiazin, KombinationenR05GC01Ref":
  from [Medication: Code 'R05GC01' from atc] M
    return 'Medication/' + M.id

define "Codein, KombinationenR05DA54Ref":
  from [Medication: Code 'R05DA54' from atc] M
    return 'Medication/' + M.id

define DimethoxanatR05DB28Ref:
  from [Medication: Code 'R05DB28' from atc] M
    return 'Medication/' + M.id

define "Pflanzliche Antitussiva und ExpektoranzienR05FPRef":
  from [Medication: Code 'R05FP' from atc] M
    return 'Medication/' + M.id

define "Antitussiva, Exkl. Kombinationen Mit ExpektoranzienR05DRef":
  from [Medication: Code 'R05D' from atc] M
    return 'Medication/' + M.id

define DibunatR05DB16Ref:
  from [Medication: Code 'R05DB16' from atc] M
    return 'Medication/' + M.id

define "Ferrum phosphoricum, KombinationenR05XH52Ref":
  from [Medication: Code 'R05XH52' from atc] M
    return 'Medication/' + M.id

define PentetrazolR07AB03Ref:
  from [Medication: Code 'R07AB03' from atc] M
    return 'Medication/' + M.id

define NormethadonR05DA06Ref:
  from [Medication: Code 'R05DA06' from atc] M
    return 'Medication/' + M.id

define PiperidionR05DB23Ref:
  from [Medication: Code 'R05DB23' from atc] M
    return 'Medication/' + M.id

define MesnaR05CB05Ref:
  from [Medication: Code 'R05CB05' from atc] M
    return 'Medication/' + M.id

define "Ipecacuanha*R05CA04Ref":
  from [Medication: Code 'R05CA04' from atc] M
    return 'Medication/' + M.id

define EprazinonR05CB04Ref:
  from [Medication: Code 'R05CB04' from atc] M
    return 'Medication/' + M.id

define "EfeublätterR05CP02Ref":
  from [Medication: Code 'R05CP02' from atc] M
    return 'Medication/' + M.id

define "Aminophenazon, KombinationenR05XA05Ref":
  from [Medication: Code 'R05XA05' from atc] M
    return 'Medication/' + M.id

define AcetylcysteinR05CB01Ref:
  from [Medication: Code 'R05CB01' from atc] M
    return 'Medication/' + M.id

define "Opium-Derivate und ExpektoranzienR05FA02Ref":
  from [Medication: Code 'R05FA02' from atc] M
    return 'Medication/' + M.id

define KreosotR05CA08Ref:
  from [Medication: Code 'R05CA08' from atc] M
    return 'Medication/' + M.id

define LetosteinR05CB09Ref:
  from [Medication: Code 'R05CB09' from atc] M
    return 'Medication/' + M.id

define AsarumwurzelstockR05CP04Ref:
  from [Medication: Code 'R05CP04' from atc] M
    return 'Medication/' + M.id

define "Erythromycin, KombinationenR05GB07Ref":
  from [Medication: Code 'R05GB07' from atc] M
    return 'Medication/' + M.id

define "Tetracyclin, KombinationenR05GB02Ref":
  from [Medication: Code 'R05GB02' from atc] M
    return 'Medication/' + M.id

define "NiauliölR05CP10Ref":
  from [Medication: Code 'R05CP10' from atc] M
    return 'Medication/' + M.id

define LevoverbenonR05CA11Ref:
  from [Medication: Code 'R05CA11' from atc] M
    return 'Medication/' + M.id

define "Andere AntitussivaR05DBRef":
  from [Medication: Code 'R05DB' from atc] M
    return 'Medication/' + M.id

define GefapixantR05DB29Ref:
  from [Medication: Code 'R05DB29' from atc] M
    return 'Medication/' + M.id

define KombinationenR05CH20Ref:
  from [Medication: Code 'R05CH20' from atc] M
    return 'Medication/' + M.id

define BenproperinR05DB02Ref:
  from [Medication: Code 'R05DB02' from atc] M
    return 'Medication/' + M.id

define VerschiedeneR05FH10Ref:
  from [Medication: Code 'R05FH10' from atc] M
    return 'Medication/' + M.id

define SobrerolR05CB07Ref:
  from [Medication: Code 'R05CB07' from atc] M
    return 'Medication/' + M.id

define GuajacolsulfonatR05CA09Ref:
  from [Medication: Code 'R05CA09' from atc] M
    return 'Medication/' + M.id

define "Expektoranzien und SulfonamideR05GCRef":
  from [Medication: Code 'R05GC' from atc] M
    return 'Medication/' + M.id

define "Phenylbutazon, KombinationenR05XA10Ref":
  from [Medication: Code 'R05XA10' from atc] M
    return 'Medication/' + M.id

define KombinationenR07AA30Ref:
  from [Medication: Code 'R07AA30' from atc] M
    return 'Medication/' + M.id

define "Sulfanilamid, KombinationenR05GC03Ref":
  from [Medication: Code 'R05GC03' from atc] M
    return 'Medication/' + M.id

define KombinationenR07AH20Ref:
  from [Medication: Code 'R07AH20' from atc] M
    return 'Medication/' + M.id

define NoscapinR05DA07Ref:
  from [Medication: Code 'R05DA07' from atc] M
    return 'Medication/' + M.id

define "Cuprum aceticumR05CH01Ref":
  from [Medication: Code 'R05CH01' from atc] M
    return 'Medication/' + M.id

define KombinationenR05FH20Ref:
  from [Medication: Code 'R05FH20' from atc] M
    return 'Medication/' + M.id

define "Andere Atemstimulanzien, KombinationenR07AB50Ref":
  from [Medication: Code 'R07AB50' from atc] M
    return 'Medication/' + M.id

define CineolR05CA13Ref:
  from [Medication: Code 'R05CA13' from atc] M
    return 'Medication/' + M.id

define IpecacuanhaR05CP11Ref:
  from [Medication: Code 'R05CP11' from atc] M
    return 'Medication/' + M.id

define MannitolR05CB16Ref:
  from [Medication: Code 'R05CB16' from atc] M
    return 'Medication/' + M.id

define DextromethorphanR05DA09Ref:
  from [Medication: Code 'R05DA09' from atc] M
    return 'Medication/' + M.id

define "SüßholzwurzelR05CP17Ref":
  from [Medication: Code 'R05CP17' from atc] M
    return 'Medication/' + M.id

define "Homöopathische und anthroposophische ExpektoranzienR05CHRef":
  from [Medication: Code 'R05CH' from atc] M
    return 'Medication/' + M.id

define "Altheae radix*R05CA05Ref":
  from [Medication: Code 'R05CA05' from atc] M
    return 'Medication/' + M.id

define "Propyphenazon, KombinationenR05XA03Ref":
  from [Medication: Code 'R05XA03' from atc] M
    return 'Medication/' + M.id

define AndornkrautR05CP16Ref:
  from [Medication: Code 'R05CP16' from atc] M
    return 'Medication/' + M.id

define SonnentaukrautR05DP01Ref:
  from [Medication: Code 'R05DP01' from atc] M
    return 'Medication/' + M.id

define ButamiratR05DB13Ref:
  from [Medication: Code 'R05DB13' from atc] M
    return 'Medication/' + M.id

define PentoxyverinR05DB05Ref:
  from [Medication: Code 'R05DB05' from atc] M
    return 'Medication/' + M.id

define AntimonpentasulfidR05CA07Ref:
  from [Medication: Code 'R05CA07' from atc] M
    return 'Medication/' + M.id

define WollblumenR05CP15Ref:
  from [Medication: Code 'R05CP15' from atc] M
    return 'Medication/' + M.id

define "Homöopathische und anthroposophische Antitussiva und ExpektoranzienR05FHRef":
  from [Medication: Code 'R05FH' from atc] M
    return 'Medication/' + M.id

define "Cefaclor, KombinationenR05GB06Ref":
  from [Medication: Code 'R05GB06' from atc] M
    return 'Medication/' + M.id

define "Analgetika-haltige Mittel gegen ErkältungskrankheitenR05XARef":
  from [Medication: Code 'R05XA' from atc] M
    return 'Medication/' + M.id

define "Paracetamol, KombinationenR05XA01Ref":
  from [Medication: Code 'R05XA01' from atc] M
    return 'Medication/' + M.id

define "Doxycyclin, KombinationenR05GA01Ref":
  from [Medication: Code 'R05GA01' from atc] M
    return 'Medication/' + M.id

define CodeinR05DA04Ref:
  from [Medication: Code 'R05DA04' from atc] M
    return 'Medication/' + M.id

define "Surfactant-PräparateR07AARef":
  from [Medication: Code 'R07AA' from atc] M
    return 'Medication/' + M.id

define "Expektoranzien, Exkl. Kombinationen Mit AntitussivaR05CRef":
  from [Medication: Code 'R05C' from atc] M
    return 'Medication/' + M.id

define MorclofonR05DB25Ref:
  from [Medication: Code 'R05DB25' from atc] M
    return 'Medication/' + M.id

define NeltenexinR05CB14Ref:
  from [Medication: Code 'R05CB14' from atc] M
    return 'Medication/' + M.id

define "Antitussiva Und Expektoranzien, KombinationenR05FRef":
  from [Medication: Code 'R05F' from atc] M
    return 'Medication/' + M.id

define "Opium-Derivate und MukolytikaR05FA01Ref":
  from [Medication: Code 'R05FA01' from atc] M
    return 'Medication/' + M.id

define "Natürliche Phospholipide*R07AA02Ref":
  from [Medication: Code 'R07AA02' from atc] M
    return 'Medication/' + M.id

define "Expektoranzien und AntibiotikaR05GBRef":
  from [Medication: Code 'R05GB' from atc] M
    return 'Medication/' + M.id

define DimeflinR07AB08Ref:
  from [Medication: Code 'R07AB08' from atc] M
    return 'Medication/' + M.id

define DroxypropinR05DB17Ref:
  from [Medication: Code 'R05DB17' from atc] M
    return 'Medication/' + M.id

define CaseinhydrolysatR05CA26Ref:
  from [Medication: Code 'R05CA26' from atc] M
    return 'Medication/' + M.id

define "Tetracyclin, KombinationenR05GA02Ref":
  from [Medication: Code 'R05GA02' from atc] M
    return 'Medication/' + M.id

define "Natriumsalicylat, KombinationenR05XA06Ref":
  from [Medication: Code 'R05XA06' from atc] M
    return 'Medication/' + M.id

define OxeladinR05DB09Ref:
  from [Medication: Code 'R05DB09' from atc] M
    return 'Medication/' + M.id

define DropropizinR05DB19Ref:
  from [Medication: Code 'R05DB19' from atc] M
    return 'Medication/' + M.id

define KaliumiodidR05CA02Ref:
  from [Medication: Code 'R05CA02' from atc] M
    return 'Medication/' + M.id

define MukolytikaR05CBRef:
  from [Medication: Code 'R05CB' from atc] M
    return 'Medication/' + M.id

define "Clobutinol, KombinationenR05DB53Ref":
  from [Medication: Code 'R05DB53' from atc] M
    return 'Medication/' + M.id

define "Thiamphenicol, KombinationenR05GB04Ref":
  from [Medication: Code 'R05GB04' from atc] M
    return 'Medication/' + M.id

define PrimelwurzelR05CP03Ref:
  from [Medication: Code 'R05CP03' from atc] M
    return 'Medication/' + M.id

define "EukalyptusölR05CP09Ref":
  from [Medication: Code 'R05CP09' from atc] M
    return 'Medication/' + M.id

define "Natürliche Phospholipide aus SchweinelungeR07AA04Ref":
  from [Medication: Code 'R07AA04' from atc] M
    return 'Medication/' + M.id

define BenzonatatR05DB01Ref:
  from [Medication: Code 'R05DB01' from atc] M
    return 'Medication/' + M.id

define "Nikethamid, KombinationenR07AB52Ref":
  from [Medication: Code 'R07AB52' from atc] M
    return 'Medication/' + M.id

define "Pflanzliche AntitussivaR05DPRef":
  from [Medication: Code 'R05DP' from atc] M
    return 'Medication/' + M.id

define "Enzym-haltige KombinationenR05XC01Ref":
  from [Medication: Code 'R05XC01' from atc] M
    return 'Medication/' + M.id

define "Andere Antitussiva und ExpektoranzienR05FBRef":
  from [Medication: Code 'R05FB' from atc] M
    return 'Medication/' + M.id

define "Andere Mukolytika, KombinationenR05CB50Ref":
  from [Medication: Code 'R05CB50' from atc] M
    return 'Medication/' + M.id

define AlmitrinR07AB07Ref:
  from [Medication: Code 'R07AB07' from atc] M
    return 'Medication/' + M.id

define "Oxytetracyclin, KombinationenR05GB03Ref":
  from [Medication: Code 'R05GB03' from atc] M
    return 'Medication/' + M.id

define PipazetatR05DB11Ref:
  from [Medication: Code 'R05DB11' from atc] M
    return 'Medication/' + M.id

define PrethcamidR07AB06Ref:
  from [Medication: Code 'R07AB06' from atc] M
    return 'Medication/' + M.id

define "Andere pflanzliche Mittel gegen Erkältungskrankheiten, KombinationenR05XP50Ref":
  from [Medication: Code 'R05XP50' from atc] M
    return 'Medication/' + M.id

define SpitzwegerichR05DP04Ref:
  from [Medication: Code 'R05DP04' from atc] M
    return 'Medication/' + M.id

define "Eukalyptusöl, KombinationenR05CP59Ref":
  from [Medication: Code 'R05CP59' from atc] M
    return 'Medication/' + M.id

define KombinationenR05DA20Ref:
  from [Medication: Code 'R05DA20' from atc] M
    return 'Medication/' + M.id

define IvacaftorR07AX02Ref:
  from [Medication: Code 'R07AX02' from atc] M
    return 'Medication/' + M.id

define ThymiankrautR05CP01Ref:
  from [Medication: Code 'R05CP01' from atc] M
    return 'Medication/' + M.id

define "Ivacaftor, Tezacaftor und ElexacaftorR07AX32Ref":
  from [Medication: Code 'R07AX32' from atc] M
    return 'Medication/' + M.id

define "Andere Mittel für den RespirationstraktR07AXRef":
  from [Medication: Code 'R07AX' from atc] M
    return 'Medication/' + M.id

define BibenzoniumbromidR05DB12Ref:
  from [Medication: Code 'R05DB12' from atc] M
    return 'Medication/' + M.id

define AcetyldihydrocodeinR05DA12Ref:
  from [Medication: Code 'R05DA12' from atc] M
    return 'Medication/' + M.id

define "Metamizol, KombinationenR05XA07Ref":
  from [Medication: Code 'R05XA07' from atc] M
    return 'Medication/' + M.id

define VerschiedeneR07AH10Ref:
  from [Medication: Code 'R07AH10' from atc] M
    return 'Medication/' + M.id

define SteproninR05CB11Ref:
  from [Medication: Code 'R05CB11' from atc] M
    return 'Medication/' + M.id

define AmbroxolR07AA03Ref:
  from [Medication: Code 'R07AA03' from atc] M
    return 'Medication/' + M.id

define "Andere Mittel gegen ErkältungskrankheitenR05XCRef":
  from [Medication: Code 'R05XC' from atc] M
    return 'Medication/' + M.id

define "Senega*R05CA06Ref":
  from [Medication: Code 'R05CA06' from atc] M
    return 'Medication/' + M.id

define MenadiolR05DB31Ref:
  from [Medication: Code 'R05DB31' from atc] M
    return 'Medication/' + M.id

define "Tyloxapol, KombinationenR05CA51Ref":
  from [Medication: Code 'R05CA51' from atc] M
    return 'Medication/' + M.id

define KombinationenR05CB10Ref:
  from [Medication: Code 'R05CB10' from atc] M
    return 'Medication/' + M.id

define "Acetylsalicylsäure und PseudoephedrinR05XA22Ref":
  from [Medication: Code 'R05XA22' from atc] M
    return 'Medication/' + M.id

define "Hederae helicis folium*R05CA12Ref":
  from [Medication: Code 'R05CA12' from atc] M
    return 'Medication/' + M.id

define "Andere Opium-Alkaloide und Derivate, KombinationenR05DA50Ref":
  from [Medication: Code 'R05DA50' from atc] M
    return 'Medication/' + M.id

define ColfoscerilpalmitatR07AA01Ref:
  from [Medication: Code 'R07AA01' from atc] M
    return 'Medication/' + M.id

define KombinationenR05CP30Ref:
  from [Medication: Code 'R05CP30' from atc] M
    return 'Medication/' + M.id

define "Pentetrazol, KombinationenR07AB53Ref":
  from [Medication: Code 'R07AB53' from atc] M
    return 'Medication/' + M.id

define "Emser SalzR05CA22Ref":
  from [Medication: Code 'R05CA22' from atc] M
    return 'Medication/' + M.id

define "Andere Mittel Für Den RespirationstraktR07Ref":
  from [Medication: Code 'R07' from atc] M
    return 'Medication/' + M.id

define "Opium-Alkaloide mit MorphinR05DA05Ref":
  from [Medication: Code 'R05DA05' from atc] M
    return 'Medication/' + M.id

define DoxapramR07AB01Ref:
  from [Medication: Code 'R07AB01' from atc] M
    return 'Medication/' + M.id

define ExpektoranzienR05CARef:
  from [Medication: Code 'R05CA' from atc] M
    return 'Medication/' + M.id

define KombinationenR05DB20Ref:
  from [Medication: Code 'R05DB20' from atc] M
    return 'Medication/' + M.id

define "Isländisches MoosR05DP02Ref":
  from [Medication: Code 'R05DP02' from atc] M
    return 'Medication/' + M.id

define "Homöopathische und anthroposophische Mittel gegen ErkältungskrankheitenR05XHRef":
  from [Medication: Code 'R05XH' from atc] M
    return 'Medication/' + M.id

define "Pentoxyverin, KombinationenR05DB55Ref":
  from [Medication: Code 'R05DB55' from atc] M
    return 'Medication/' + M.id

define "Prenoxdiazin, KombinationenR05DB68Ref":
  from [Medication: Code 'R05DB68' from atc] M
    return 'Medication/' + M.id

define "Andere homöopathische und anthroposophische Mittel für den RespirationstraktR07AHRef":
  from [Medication: Code 'R07AH' from atc] M
    return 'Medication/' + M.id

define KombinationenR05XH20Ref:
  from [Medication: Code 'R05XH20' from atc] M
    return 'Medication/' + M.id

define "Echinacea-purpurea-PresssaftR05XP01Ref":
  from [Medication: Code 'R05XP01' from atc] M
    return 'Medication/' + M.id

define "HuflattichblätterR05DP07Ref":
  from [Medication: Code 'R05DP07' from atc] M
    return 'Medication/' + M.id

define "Opium-Derivate und ExpektoranzienR05FARef":
  from [Medication: Code 'R05FA' from atc] M
    return 'Medication/' + M.id

define "Thymiankraut, KombinationenR05CP51Ref":
  from [Medication: Code 'R05CP51' from atc] M
    return 'Medication/' + M.id

define "Antitussiva und MukolytikaR05FB01Ref":
  from [Medication: Code 'R05FB01' from atc] M
    return 'Medication/' + M.id

define ClobutinolR05DB03Ref:
  from [Medication: Code 'R05DB03' from atc] M
    return 'Medication/' + M.id

define "Eibischwurzel und -blätterR05DP05Ref":
  from [Medication: Code 'R05DP05' from atc] M
    return 'Medication/' + M.id

define ClofedanolR05DB10Ref:
  from [Medication: Code 'R05DB10' from atc] M
    return 'Medication/' + M.id

define "Phenacetin, KombinationenR05XA12Ref":
  from [Medication: Code 'R05XA12' from atc] M
    return 'Medication/' + M.id

define AmbroxolR05CB06Ref:
  from [Medication: Code 'R05CB06' from atc] M
    return 'Medication/' + M.id

define "Opium-Alkaloide und DerivateR05DARef":
  from [Medication: Code 'R05DA' from atc] M
    return 'Medication/' + M.id

define FominobenR05DB41Ref:
  from [Medication: Code 'R05DB41' from atc] M
    return 'Medication/' + M.id

define IsoaminilR05DB04Ref:
  from [Medication: Code 'R05DB04' from atc] M
    return 'Medication/' + M.id

define "Dornase alfa (Desoxyribonuclease)R05CB13Ref":
  from [Medication: Code 'R05CB13' from atc] M
    return 'Medication/' + M.id

define "Antitussiva und ExpektoranzienR05FB02Ref":
  from [Medication: Code 'R05FB02' from atc] M
    return 'Medication/' + M.id

define "Bromhexin, KombinationenR05CB52Ref":
  from [Medication: Code 'R05CB52' from atc] M
    return 'Medication/' + M.id

define "Echinacea angustifolia, KombinationenR05XH51Ref":
  from [Medication: Code 'R05XH51' from atc] M
    return 'Medication/' + M.id

define "Butetamat, KombinationenR05DB80Ref":
  from [Medication: Code 'R05DB80' from atc] M
    return 'Medication/' + M.id

define "Phenazon, KombinationenR05XA08Ref":
  from [Medication: Code 'R05XA08' from atc] M
    return 'Medication/' + M.id

define "Doxycyclin, KombinationenR05GB51Ref":
  from [Medication: Code 'R05GB51' from atc] M
    return 'Medication/' + M.id

define CarbocisteinR05CB03Ref:
  from [Medication: Code 'R05CB03' from atc] M
    return 'Medication/' + M.id

define ZipeprolR05DB15Ref:
  from [Medication: Code 'R05DB15' from atc] M
    return 'Medication/' + M.id

define "Andere Expektoranzien, KombinationenR05CA50Ref":
  from [Medication: Code 'R05CA50' from atc] M
    return 'Medication/' + M.id

define PelargoniumwurzelR05CP05Ref:
  from [Medication: Code 'R05CP05' from atc] M
    return 'Medication/' + M.id

define LevodropropizinR05DB27Ref:
  from [Medication: Code 'R05DB27' from atc] M
    return 'Medication/' + M.id

define "Antitussiva und AntibiotikaR05GARef":
  from [Medication: Code 'R05GA' from atc] M
    return 'Medication/' + M.id

define PholcodinR05DA08Ref:
  from [Medication: Code 'R05DA08' from atc] M
    return 'Medication/' + M.id

define "Ampicillin, KombinationenR05GB05Ref":
  from [Medication: Code 'R05GB05' from atc] M
    return 'Medication/' + M.id

define DimemorfanR05DA11Ref:
  from [Medication: Code 'R05DA11' from atc] M
    return 'Medication/' + M.id

define "Husten- Und ErkältungsmittelR05Ref":
  from [Medication: Code 'R05' from atc] M
    return 'Medication/' + M.id

define NepinalonR05DB26Ref:
  from [Medication: Code 'R05DB26' from atc] M
    return 'Medication/' + M.id

define "Sulfamethoxazol und TrimethoprimR05GC02Ref":
  from [Medication: Code 'R05GC02' from atc] M
    return 'Medication/' + M.id

define BemegridR07AB05Ref:
  from [Medication: Code 'R07AB05' from atc] M
    return 'Medication/' + M.id

define CloperastinR05DB21Ref:
  from [Medication: Code 'R05DB21' from atc] M
    return 'Medication/' + M.id

define KombinationenR05CA10Ref:
  from [Medication: Code 'R05CA10' from atc] M
    return 'Medication/' + M.id

define MeprotixolR05DB22Ref:
  from [Medication: Code 'R05DB22' from atc] M
    return 'Medication/' + M.id

define "Normethadon, KombinationenR05DA56Ref":
  from [Medication: Code 'R05DA56' from atc] M
    return 'Medication/' + M.id

define "Natürliche Phospholipide aus RinderlungeR07AA05Ref":
  from [Medication: Code 'R07AA05' from atc] M
    return 'Medication/' + M.id

define "Pflanzliche Antitussiva und Expektoranzien, KombinationenR05FP30Ref":
  from [Medication: Code 'R05FP30' from atc] M
    return 'Medication/' + M.id

define "Acetylsalicylsäure, KombinationenR05XA02Ref":
  from [Medication: Code 'R05XA02' from atc] M
    return 'Medication/' + M.id

define FedrilatR05DB14Ref:
  from [Medication: Code 'R05DB14' from atc] M
    return 'Medication/' + M.id

define SenegawurzelR05CP13Ref:
  from [Medication: Code 'R05CP13' from atc] M
    return 'Medication/' + M.id

define ErdosteinR05CB15Ref:
  from [Medication: Code 'R05CB15' from atc] M
    return 'Medication/' + M.id

define "Ethenzamid, KombinationenR05XA04Ref":
  from [Medication: Code 'R05XA04' from atc] M
    return 'Medication/' + M.id

define "Andere Mittel Für Den RespirationstraktR07ARef":
  from [Medication: Code 'R07A' from atc] M
    return 'Medication/' + M.id

define EtamivanR07AB04Ref:
  from [Medication: Code 'R07AB04' from atc] M
    return 'Medication/' + M.id

define NikethamidR07AB02Ref:
  from [Medication: Code 'R07AB02' from atc] M
    return 'Medication/' + M.id

define EthylmorphinR05DA01Ref:
  from [Medication: Code 'R05DA01' from atc] M
    return 'Medication/' + M.id

define TipepidinR05DB24Ref:
  from [Medication: Code 'R05DB24' from atc] M
    return 'Medication/' + M.id

define CinnabarisR05XH04Ref:
  from [Medication: Code 'R05XH04' from atc] M
    return 'Medication/' + M.id

define "Pflanzliche Kombinationen mit anderen MittelnR05XC02Ref":
  from [Medication: Code 'R05XC02' from atc] M
    return 'Medication/' + M.id

define "SpikölR05CP08Ref":
  from [Medication: Code 'R05CP08' from atc] M
    return 'Medication/' + M.id

define PrenoxdiazinR05DB18Ref:
  from [Medication: Code 'R05DB18' from atc] M
    return 'Medication/' + M.id

define "Ferrum phosphoricumR05XH02Ref":
  from [Medication: Code 'R05XH02' from atc] M
    return 'Medication/' + M.id

define GuaifenesinR05CA03Ref:
  from [Medication: Code 'R05CA03' from atc] M
    return 'Medication/' + M.id

define TyloxapolR05CA01Ref:
  from [Medication: Code 'R05CA01' from atc] M
    return 'Medication/' + M.id

define DihydrocodeinR05DA14Ref:
  from [Medication: Code 'R05DA14' from atc] M
    return 'Medication/' + M.id

define "Antitussiva Und Expektoranzien, Kombinationen Mit AntibiotikaR05GRef":
  from [Medication: Code 'R05G' from atc] M
    return 'Medication/' + M.id

define ButetamatR05DB30Ref:
  from [Medication: Code 'R05DB30' from atc] M
    return 'Medication/' + M.id

define "Metacyclin, KombinationenR05GB08Ref":
  from [Medication: Code 'R05GB08' from atc] M
    return 'Medication/' + M.id

define NicocodinR05DA13Ref:
  from [Medication: Code 'R05DA13' from atc] M
    return 'Medication/' + M.id

define "Isoaminil, KombinationenR05DB54Ref":
  from [Medication: Code 'R05DB54' from atc] M
    return 'Medication/' + M.id

define AtemstimulanzienR07ABRef:
  from [Medication: Code 'R07AB' from atc] M
    return 'Medication/' + M.id

define "Dihydrocodein, KombinationenR05DA64Ref":
  from [Medication: Code 'R05DA64' from atc] M
    return 'Medication/' + M.id

define StickoxidR07AX01Ref:
  from [Medication: Code 'R07AX01' from atc] M
    return 'Medication/' + M.id

define OxolaminR05DB07Ref:
  from [Medication: Code 'R05DB07' from atc] M
    return 'Medication/' + M.id

define MepixanoxR07AB09Ref:
  from [Medication: Code 'R07AB09' from atc] M
    return 'Medication/' + M.id

define "Pflanzliche ExpektoranzienR05CPRef":
  from [Medication: Code 'R05CP' from atc] M
    return 'Medication/' + M.id

define "Kombinationen mit anderen MittelnR05CH50Ref":
  from [Medication: Code 'R05CH50' from atc] M
    return 'Medication/' + M.id

define "Ivacaftor und TezacaftorR07AX31Ref":
  from [Medication: Code 'R07AX31' from atc] M
    return 'Medication/' + M.id

define ThebaconR05DA10Ref:
  from [Medication: Code 'R05DA10' from atc] M
    return 'Medication/' + M.id

define "Doxycyclin mit AmbroxolR05GB01Ref":
  from [Medication: Code 'R05GB01' from atc] M
    return 'Medication/' + M.id

define BromhexinR05CB02Ref:
  from [Medication: Code 'R05CB02' from atc] M
    return 'Medication/' + M.id

context Patient

define Inclusion:
  (exists [Condition: Code 'C50.0' from icd10] or
  Patient.gender = 'female' or
  Patient.gender = 'male' or
  Patient.gender = 'other' or
  Patient.gender = 'unknown' or
  exists (from [Condition: Code 'C00-C97' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C97-C97' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C97' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C00-C75' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C30-C39' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C34' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C34.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C34.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C34.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C34.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C34.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C34.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C30' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C30.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C30.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C31' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C31.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C31.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C31.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C31.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C31.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C31.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C33' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C38' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C38.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C38.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C38.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C38.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C38.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C38.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C32' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C32.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C32.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C32.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C32.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C32.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C32.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C37' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C39' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C39.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C39.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C39.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C50-C50' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C50' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C50.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C50.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C50.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C50.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C50.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C50.6' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C50.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C50.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C50.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C64-C68' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C67' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C67.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C67.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C67.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C67.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C67.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C67.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C67.6' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C67.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C67.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C67.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C64' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C65' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C66' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C68' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C68.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C68.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C68.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C68.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C00-C14' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C00' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C00.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C00.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C00.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C00.6' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C00.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C00.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C00.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C00.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C00.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C07' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C09' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C09.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C09.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C09.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C09.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C05' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C05.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C05.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C05.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C05.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C05.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C13' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C13.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C13.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C13.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C13.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C13.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C04' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C04.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C04.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C04.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C04.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C11' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C11.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C11.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C11.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C11.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C11.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C11.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C10' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C10.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C10.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C10.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C10.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C10.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C10.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C10.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C12' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C03' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C03.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C03.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C03.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C01' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C08' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C08.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C08.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C08.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C08.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C02' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C02.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C02.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C02.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C02.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C02.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C02.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C02.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C06' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C06.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C06.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C06.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C06.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C06.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C14' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C14.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C14.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C14.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C60-C63' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C61' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C62' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C62.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C62.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C62.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C60' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C60.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C60.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C60.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C60.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C60.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C63' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C63.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C63.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C63.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C63.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C63.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C63.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C73-C75' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C74' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C74.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C74.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C74.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C73' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C75' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C75.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C75.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C75.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C75.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C75.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C75.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C75.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C75.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C15-C26' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C19' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C23' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C22' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C22.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C22.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C22.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C22.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C22.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C22.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C22.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C21' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C21.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C21.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C21.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C21.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C17' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C17.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C17.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C17.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C17.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C17.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C17.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C18' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C18.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C18.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C18.6' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C18.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C18.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C18.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C18.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C18.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C18.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C18.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C16' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C16.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C16.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C16.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C16.6' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C16.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C16.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C16.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C16.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C16.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C25' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C25.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C25.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C25.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C25.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C25.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C25.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C25.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C25.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C20' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C15' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C15.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C15.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C15.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C15.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C15.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C15.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C15.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C15.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C24' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C24.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C24.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C24.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C24.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C26' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C26.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C26.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C26.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C26.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C51-C58' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C53' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C53.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C53.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C53.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C53.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C58' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C52' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C51' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C51.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C51.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C51.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C51.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C51.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C54' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C54.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C54.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C54.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C54.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C54.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C54.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C56' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C55' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C57' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C57.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C57.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C57.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C57.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C57.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C57.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C57.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C57.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C69-C72' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C70' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C70.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C70.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C70.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C69' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C69.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C69.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C69.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C69.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C69.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C69.6' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C69.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C69.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C69.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C71' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C71.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C71.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C71.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C71.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C71.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C71.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C71.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C71.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C71.6' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C71.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C72' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C72.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C72.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C72.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C72.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C72.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C72.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C72.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C72.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C40-C41' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C40' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C40.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C40.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C40.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C40.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C40.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C40.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C41' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C41.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C41.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C41.01' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C41.02' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C41.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C41.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C41.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C41.32' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C41.30' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C41.31' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C41.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C41.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C45-C49' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C47' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C47.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C47.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C47.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C47.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C47.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C47.6' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C47.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C47.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C47.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C48' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C48.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C48.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C48.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C48.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C49' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C49.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C49.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C49.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C49.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C49.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C49.6' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C49.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C49.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C49.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C46' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C46.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C46.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C46.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C46.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C46.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C46.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C46.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C45' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C45.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C45.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C45.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C45.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C45.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C43-C44' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C43' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C43.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C43.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C43.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C43.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C43.6' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C43.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C43.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C43.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C43.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C43.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C44' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C44.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C44.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C44.6' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C44.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C44.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C44.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C44.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C44.59' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C44.50' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C44.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C44.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C44.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C81-C96' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C88' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C88.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C88.91' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C88.90' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C88.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C88.41' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C88.40' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C88.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C88.31' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C88.30' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C88.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C88.01' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C88.00' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C88.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C88.71' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C88.70' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C88.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C88.21' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C88.20' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C82' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C82.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C82.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C82.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C82.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C82.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C82.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C82.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C82.6' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C82.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C81' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C81.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C81.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C81.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C81.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C81.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C81.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C81.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C95' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C95.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C95.01' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C95.00' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C95.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C95.11' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C95.10' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C95.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C95.91' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C95.90' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C95.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C95.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C95.71' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C95.70' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.51' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.50' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.01' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.00' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.11' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.10' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.41' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.40' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.91' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.90' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.6' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.61' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.60' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.31' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.30' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.81' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.80' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.71' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C91.70' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C93' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C93.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C93.01' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C93.00' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C93.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C93.11' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C93.10' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C93.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C93.31' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C93.30' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C93.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C93.91' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C93.90' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C93.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C93.71' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C93.70' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.01' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.00' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.6' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.61' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.60' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.81' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.80' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.51' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.50' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.41' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.40' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.21' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.20' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.11' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.10' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.91' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.90' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.31' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.30' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.71' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C92.70' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C83' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C83.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C83.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C83.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C83.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C83.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C83.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C83.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C90' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C90.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C90.21' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C90.20' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C90.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C90.01' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C90.00' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C90.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C90.11' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C90.10' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C90.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C90.31' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C90.30' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C84' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C84.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C84.6' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C84.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C84.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C84.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C84.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C84.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C84.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C94' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C94.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C94.01' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C94.00' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C94.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C94.21' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C94.20' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C94.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C94.41' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C94.40' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C94.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C94.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C94.31' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C94.30' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C94.6' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C94.61' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C94.60' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C94.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C94.71' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C94.70' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C96' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C96.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C96.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C96.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C96.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C96.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C96.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C96.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C96.6' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C85' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C85.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C85.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C85.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C85.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C86' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C86.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C86.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C86.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C86.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C86.6' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C86.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C86.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C76-C80' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C80' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C80.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C80.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C76' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C76.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C76.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C76.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C76.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C76.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C76.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C76.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C76.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C79' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C79.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C79.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C79.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C79.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C79.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C79.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C79.6' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C79.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C79.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C79.81' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C79.82' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C79.86' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C79.85' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C79.83' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C79.88' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C79.84' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C79.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C78' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C78.7' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C78.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C78.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C78.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C78.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C78.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C78.6' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C78.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C78.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C77' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C77.3' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C77.4' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C77.2' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C77.5' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C77.1' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C77.0' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C77.8' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23]) or
  exists (from [Condition: Code 'C77.9' from icd10] C
    where ToDate(C.onset as dateTime) in Interval[@2017-02-07, @2023-11-23] or
      C.onset overlaps Interval[@2017-02-07, @2023-11-23])) and
  (exists [Procedure: Code '1-90...1-99' from oops] or
  exists [Procedure: Code '1-99' from oops] or
  exists [Procedure: Code '1-995' from oops] or
  exists [Procedure: Code '1-995.3' from oops] or
  exists [Procedure: Code '1-995.30' from oops] or
  exists [Procedure: Code '1-995.33' from oops] or
  exists [Procedure: Code '1-995.31' from oops] or
  exists [Procedure: Code '1-995.32' from oops] or
  exists [Procedure: Code '1-995.2' from oops] or
  exists [Procedure: Code '1-995.20' from oops] or
  exists [Procedure: Code '1-995.23' from oops] or
  exists [Procedure: Code '1-995.21' from oops] or
  exists [Procedure: Code '1-995.22' from oops] or
  exists [Procedure: Code '1-995.0' from oops] or
  exists [Procedure: Code '1-995.00' from oops] or
  exists [Procedure: Code '1-995.03' from oops] or
  exists [Procedure: Code '1-995.01' from oops] or
  exists [Procedure: Code '1-995.02' from oops] or
  exists [Procedure: Code '1-995.1' from oops] or
  exists [Procedure: Code '1-995.10' from oops] or
  exists [Procedure: Code '1-995.13' from oops] or
  exists [Procedure: Code '1-995.11' from oops] or
  exists [Procedure: Code '1-995.12' from oops] or
  exists [Procedure: Code '1-993' from oops] or
  exists [Procedure: Code '1-997' from oops] or
  exists [Procedure: Code '1-997.1' from oops] or
  exists [Procedure: Code '1-997.10' from oops] or
  exists [Procedure: Code '1-997.12' from oops] or
  exists [Procedure: Code '1-997.13' from oops] or
  exists [Procedure: Code '1-997.14' from oops] or
  exists [Procedure: Code '1-997.15' from oops] or
  exists [Procedure: Code '1-997.16' from oops] or
  exists [Procedure: Code '1-997.17' from oops] or
  exists [Procedure: Code '1-997.18' from oops] or
  exists [Procedure: Code '1-997.11' from oops] or
  exists [Procedure: Code '1-997.0' from oops] or
  exists [Procedure: Code '1-997.00' from oops] or
  exists [Procedure: Code '1-997.02' from oops] or
  exists [Procedure: Code '1-997.03' from oops] or
  exists [Procedure: Code '1-997.04' from oops] or
  exists [Procedure: Code '1-997.05' from oops] or
  exists [Procedure: Code '1-997.06' from oops] or
  exists [Procedure: Code '1-997.07' from oops] or
  exists [Procedure: Code '1-997.08' from oops] or
  exists [Procedure: Code '1-997.01' from oops] or
  exists [Procedure: Code '1-992' from oops] or
  exists [Procedure: Code '1-992.0' from oops] or
  exists [Procedure: Code '1-992.3' from oops] or
  exists [Procedure: Code '1-992.2' from oops] or
  exists [Procedure: Code '1-990' from oops] or
  exists [Procedure: Code '1-990.0' from oops] or
  exists [Procedure: Code '1-990.2' from oops] or
  exists [Procedure: Code '1-990.1' from oops] or
  exists [Procedure: Code '1-990.x' from oops] or
  exists [Procedure: Code '1-996' from oops] or
  exists [Procedure: Code '1-996.1' from oops] or
  exists [Procedure: Code '1-996.10' from oops] or
  exists [Procedure: Code '1-996.12' from oops] or
  exists [Procedure: Code '1-996.13' from oops] or
  exists [Procedure: Code '1-996.14' from oops] or
  exists [Procedure: Code '1-996.15' from oops] or
  exists [Procedure: Code '1-996.16' from oops] or
  exists [Procedure: Code '1-996.17' from oops] or
  exists [Procedure: Code '1-996.18' from oops] or
  exists [Procedure: Code '1-996.11' from oops] or
  exists [Procedure: Code '1-996.0' from oops] or
  exists [Procedure: Code '1-996.00' from oops] or
  exists [Procedure: Code '1-996.02' from oops] or
  exists [Procedure: Code '1-996.03' from oops] or
  exists [Procedure: Code '1-996.04' from oops] or
  exists [Procedure: Code '1-996.05' from oops] or
  exists [Procedure: Code '1-996.06' from oops] or
  exists [Procedure: Code '1-996.07' from oops] or
  exists [Procedure: Code '1-996.08' from oops] or
  exists [Procedure: Code '1-996.01' from oops] or
  exists [Procedure: Code '1-994' from oops] or
  exists [Procedure: Code '1-991' from oops] or
  exists [Procedure: Code '1-991.3' from oops] or
  exists [Procedure: Code '1-991.0' from oops] or
  exists [Procedure: Code '1-991.2' from oops] or
  exists [Procedure: Code '1-991.1' from oops] or
  exists [Procedure: Code '1-999' from oops] or
  exists [Procedure: Code '1-999.0' from oops] or
  exists [Procedure: Code '1-999.01' from oops] or
  exists [Procedure: Code '1-999.03' from oops] or
  exists [Procedure: Code '1-999.04' from oops] or
  exists [Procedure: Code '1-999.00' from oops] or
  exists [Procedure: Code '1-999.02' from oops] or
  exists [Procedure: Code '1-999.0x' from oops] or
  exists [Procedure: Code '1-999.4' from oops] or
  exists [Procedure: Code '1-999.41' from oops] or
  exists [Procedure: Code '1-999.42' from oops] or
  exists [Procedure: Code '1-999.4x' from oops] or
  exists [Procedure: Code '1-999.40' from oops] or
  exists [Procedure: Code '1-999.2' from oops] or
  exists [Procedure: Code '1-999.20' from oops] or
  exists [Procedure: Code '1-999.2x' from oops] or
  exists [Procedure: Code '1-999.1' from oops] or
  exists [Procedure: Code '1-999.3' from oops] or
  exists [Procedure: Code '1-91' from oops] or
  exists [Procedure: Code '1-911' from oops] or
  exists [Procedure: Code '1-910' from oops] or
  exists [Procedure: Code '1-912' from oops] or
  exists [Procedure: Code '1-93' from oops] or
  exists [Procedure: Code '1-930' from oops] or
  exists [Procedure: Code '1-930.3' from oops] or
  exists [Procedure: Code '1-930.4' from oops] or
  exists [Procedure: Code '1-930.0' from oops] or
  exists [Procedure: Code '1-930.1' from oops] or
  exists [Procedure: Code '1-931' from oops] or
  exists [Procedure: Code '1-931.1' from oops] or
  exists [Procedure: Code '1-931.0' from oops] or
  exists [Procedure: Code '1-94' from oops] or
  exists [Procedure: Code '1-944' from oops] or
  exists [Procedure: Code '1-944.2' from oops] or
  exists [Procedure: Code '1-944.21' from oops] or
  exists [Procedure: Code '1-944.20' from oops] or
  exists [Procedure: Code '1-944.1' from oops] or
  exists [Procedure: Code '1-944.11' from oops] or
  exists [Procedure: Code '1-944.10' from oops] or
  exists [Procedure: Code '1-944.3' from oops] or
  exists [Procedure: Code '1-944.31' from oops] or
  exists [Procedure: Code '1-944.30' from oops] or
  exists [Procedure: Code '1-944.0' from oops] or
  exists [Procedure: Code '1-944.01' from oops] or
  exists [Procedure: Code '1-944.00' from oops] or
  exists [Procedure: Code '1-945' from oops] or
  exists [Procedure: Code '1-945.1' from oops] or
  exists [Procedure: Code '1-945.0' from oops] or
  exists [Procedure: Code '1-940' from oops] or
  exists [Procedure: Code '1-941' from oops] or
  exists [Procedure: Code '1-941.3' from oops] or
  exists [Procedure: Code '1-941.31' from oops] or
  exists [Procedure: Code '1-941.30' from oops] or
  exists [Procedure: Code '1-941.0' from oops] or
  exists [Procedure: Code '1-941.2' from oops] or
  exists [Procedure: Code '1-941.21' from oops] or
  exists [Procedure: Code '1-941.20' from oops] or
  exists [Procedure: Code '1-943' from oops] or
  exists [Procedure: Code '1-943.2' from oops] or
  exists [Procedure: Code '1-943.3' from oops] or
  exists [Procedure: Code '1-943.1' from oops] or
  exists [Procedure: Code '1-943.0' from oops] or
  exists [Procedure: Code '1-942' from oops] or
  exists [Procedure: Code '1-942.2' from oops] or
  exists [Procedure: Code '1-942.1' from oops] or
  exists [Procedure: Code '1-942.3' from oops] or
  exists [Procedure: Code '1-942.0' from oops] or
  exists [Procedure: Code '1-92' from oops] or
  exists [Procedure: Code '1-920' from oops] or
  exists [Procedure: Code '1-920.3' from oops] or
  exists [Procedure: Code '1-920.36' from oops] or
  exists [Procedure: Code '1-920.33' from oops] or
  exists [Procedure: Code '1-920.31' from oops] or
  exists [Procedure: Code '1-920.34' from oops] or
  exists [Procedure: Code '1-920.32' from oops] or
  exists [Procedure: Code '1-920.30' from oops] or
  exists [Procedure: Code '1-920.35' from oops] or
  exists [Procedure: Code '1-920.4' from oops] or
  exists [Procedure: Code '1-920.46' from oops] or
  exists [Procedure: Code '1-920.43' from oops] or
  exists [Procedure: Code '1-920.41' from oops] or
  exists [Procedure: Code '1-920.44' from oops] or
  exists [Procedure: Code '1-920.42' from oops] or
  exists [Procedure: Code '1-920.40' from oops] or
  exists [Procedure: Code '1-920.45' from oops] or
  exists [Procedure: Code '1-920.x' from oops] or
  exists [Procedure: Code '1-920.1' from oops] or
  exists [Procedure: Code '1-920.16' from oops] or
  exists [Procedure: Code '1-920.13' from oops] or
  exists [Procedure: Code '1-920.11' from oops] or
  exists [Procedure: Code '1-920.14' from oops] or
  exists [Procedure: Code '1-920.12' from oops] or
  exists [Procedure: Code '1-920.10' from oops] or
  exists [Procedure: Code '1-920.15' from oops] or
  exists [Procedure: Code '1-920.2' from oops] or
  exists [Procedure: Code '1-920.26' from oops] or
  exists [Procedure: Code '1-920.23' from oops] or
  exists [Procedure: Code '1-920.21' from oops] or
  exists [Procedure: Code '1-920.24' from oops] or
  exists [Procedure: Code '1-920.22' from oops] or
  exists [Procedure: Code '1-920.20' from oops] or
  exists [Procedure: Code '1-920.25' from oops] or
  exists [Procedure: Code '1-920.0' from oops] or
  exists [Procedure: Code '1-920.06' from oops] or
  exists [Procedure: Code '1-920.03' from oops] or
  exists [Procedure: Code '1-920.01' from oops] or
  exists [Procedure: Code '1-920.04' from oops] or
  exists [Procedure: Code '1-920.02' from oops] or
  exists [Procedure: Code '1-920.00' from oops] or
  exists [Procedure: Code '1-920.05' from oops] or
  exists [Procedure: Code '1-90' from oops] or
  exists [Procedure: Code '1-901' from oops] or
  exists [Procedure: Code '1-901.0' from oops] or
  exists [Procedure: Code '1-901.1' from oops] or
  exists [Procedure: Code '1-900' from oops] or
  exists [Procedure: Code '1-900.0' from oops] or
  exists [Procedure: Code '1-900.1' from oops] or
  exists [Procedure: Code '1-902' from oops] or
  exists [Procedure: Code '1-902.0' from oops] or
  exists [Procedure: Code '1-902.1' from oops]) and
  (exists (from [MedicationAdministration] M
    where M.medication.reference in "Immunsera Und ImmunglobulineJ06Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ImmunglobulineJ06BRef) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Antibakterielle monoklonale AntikörperJ06BCRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BezlotoxumabJ06BC03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in NebacumabJ06BC01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ObiltoxaximabJ06BC04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in RaxibacumabJ06BC02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Antivirale monoklonale AntikörperJ06BDRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AnsuvimabJ06BD04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Casirivimab und ImdevimabJ06BD07Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MotavizumabJ06BD02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in NirsevimabJ06BD08Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PalivizumabJ06BD01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in RegdanvimabJ06BD06Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in SotrovimabJ06BD05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Tixagevimab und CilgavimabJ06BD03Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Immunglobuline, normal humanJ06BARef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Immunglobuline, normal human, zur extravasalen AnwendungJ06BA01Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Immunglobuline, normal human, zur intravasalen AnwendungJ06BA02Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Spezifische ImmunglobulineJ06BBRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Anthrax-ImmunglobulinJ06BB19Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Anti-D(rh)-ImmunglobulinJ06BB01Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Cytomegalievirus-ImmunglobulinJ06BB09Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Diphtherie-ImmunglobulinJ06BB10Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "FSME-ImmunglobulinJ06BB12Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Hepatitis-A-ImmunglobulinJ06BB11Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Hepatitis-B-ImmunglobulinJ06BB04Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in KombinationenJ06BB30Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Kuhpocken-ImmunglobulinJ06BB07Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Masern-ImmunglobulinJ06BB14Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Mumps-ImmunglobulinJ06BB15Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Pertussis-ImmunglobulinJ06BB13Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Röteln-ImmunglobulinJ06BB06Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Staphylococcus-ImmunglobulinJ06BB08Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Tetanus-ImmunglobulinJ06BB02Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Tollwut-ImmunglobulinJ06BB05Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Varicella/Zoster-ImmunglobulinJ06BB03Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ImmunseraJ06ARef) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ImmunseraJ06AARef) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Botulismus-AntitoxinJ06AA04Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Diphtherie-AntitoxinJ06AA01Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Gasbrand-SerumJ06AA05Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Schlangengift-AntiserumJ06AA03Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Tetanus-AntitoxinJ06AA02Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Tollwut-SerumJ06AA06Ref")) and
  (exists (from [MedicationAdministration] M
    where M.medication.reference in "Antineoplastische MittelL01Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Alkylierende MittelL01ARef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AlkylsulfonateL01ABRef) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BusulfanL01AB01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MannosulfanL01AB03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TreosulfanL01AB02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere alkylierende MittelL01AXRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DacarbazinL01AX04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MitobronitolL01AX01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PipobromanL01AX02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TemozolomidL01AX03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in EpoxideL01AGRef) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in EtoglucidL01AG01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in EthylenimineL01ACRef) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CarboquonL01AC03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ThiotepaL01AC01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TriaziquonL01AC02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in NitrosoharnstoffeL01ADRef) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CarmustinL01AD01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in FotemustinL01AD05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in LomustinL01AD02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in NimustinL01AD06Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in RanimustinL01AD07Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in SemustinL01AD03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in StreptozocinL01AD04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in UramustinL01AD08Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Stickstofflost-AnalogaL01AARef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BendamustinL01AA09Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ChlorambucilL01AA02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ChlormethinL01AA05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CyclophosphamidL01AA01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in IfosfamidL01AA06Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MelphalanL01AA03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MelphalanflufenamidL01AA10Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PrednimustinL01AA08Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TrofosfamidL01AA07Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere Antineoplastische MittelL01XRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere antineoplastische MittelL01XXRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AfliberceptL01XX44Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Allogene T-Zellen, genetisch modifiziertL01XX90Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AltretaminL01XX03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AmsacrinL01XX01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AnagrelidL01XX35Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ArsentrioxidL01XX27Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AsparaginaseL01XX02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Autologe T-Zellen, genetisch modifiziertL01XX91Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Axicabtagen ciloleucelL01XX70Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BelzutifanL01XX74Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CelecoxibL01XX33Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Ciltacabtagen autoleucelL01XX76Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DenileukindiftitoxL01XX29Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in EnasidenibL01XX59Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in EpacadostatL01XX58Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in EribulinL01XX41Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in EstramustinL01XX11Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in HydroxycarbamidL01XX05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Idecabtagen vicleucelL01XX77Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in IvosidenibL01XX62Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in LonidaminL01XX07Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in LurbinectedinL01XX69Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MasoprocolL01XX10Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MiltefosinL01XX09Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MitoguazonL01XX16Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MitotanL01XX23Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in OblimersenL01XX36Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in OmacetaxinmepesuccinatL01XX40Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PegaspargaseL01XX24Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PentostatinL01XX08Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PlitidepsinL01XX57Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in SelinexorL01XX66Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in SitimagenceradenovecL01XX37Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in SotorasibL01XX73Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TagraxofuspL01XX67Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Talimogen laherparepvecL01XX51Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TazemetostatL01XX72Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TebentafuspL01XX75Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TiazofurinL01XX18Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TisagenlecleucelL01XX71Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in VenetoclaxL01XX52Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in VosaroxinL01XX53Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Hedgehog-Signalweg-InhibitorenL01XJRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in GlasdegibL01XJ03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in SonidegibL01XJ02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in VismodegibL01XJ01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Histon-Deacetylase (HDAC)-InhibitorenL01XHRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BelinostatL01XH04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in EntinostatL01XH05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PanobinostatL01XH03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in RomidepsinL01XH02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in VorinostatL01XH01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Kombinationen von antineoplastischen MittelnL01XYRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Cytarabin und DaunorubicinL01XY01Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Nivolumab und RelatlimabL01XY03Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Pertuzumab und TrastuzumabL01XY02Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MethylhydrazineL01XBRef) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ProcarbazinL01XB01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Platin-haltige VerbindungenL01XARef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CarboplatinL01XA02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CisplatinL01XA01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in OxaliplatinL01XA03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PolyplatillenL01XA05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in SatraplatinL01XA04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Poly-ADP-Ribose-Polymerase (PARP)-InhibitorenL01XKRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in NiraparibL01XK02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in OlaparibL01XK01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PamiparibL01XK06Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in RucaparibL01XK03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TalazoparibL01XK04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in VeliparibL01XK05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Proteasom-InhibitorenL01XGRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BortezomibL01XG01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CarfilzomibL01XG02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in IxazomibL01XG03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Retinoide zur KrebsbehandlungL01XFRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AlitretinoinL01XF02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BexarotenL01XF03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TretinoinL01XF01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Sensibilisatoren für die photodynamische/Radio-TherapieL01XDRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "AminolevulinsäureL01XD04Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in EfaproxiralL01XD06Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MethoxsalenL01XD10Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MethylaminolevulinatL01XD03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PadeliporfinL01XD07Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Porfimer natriumL01XD01Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TemoporfinL01XD05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AntimetabolitenL01BRef) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Folsäure-AnalogaL01BARef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MethotrexatL01BA01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PemetrexedL01BA04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PralatrexatL01BA05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in RaltitrexedL01BA03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Purin-AnalogaL01BBRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CladribinL01BB04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ClofarabinL01BB06Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in FludarabinL01BB05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MercaptopurinL01BB02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in NelarabinL01BB07Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TioguaninL01BB03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Pyrimidin-AnalogaL01BCRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AzacitidinL01BC07Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CapecitabinL01BC06Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CarmofurL01BC04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CytarabinL01BC01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DecitabinL01BC08Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in FloxuridinL01BC09Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in FluorouracilL01BC02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Fluorouracil, KombinationenL01BC52Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in GemcitabinL01BC05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TegafurL01BC03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Tegafur und UracilL01BC63Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Tegafur, Gimeracil und OteracilL01BC73Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Tegafur, KombinationenL01BC53Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Trifluridin, KombinationenL01BC59Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Monoklonale Antikörper Und Antikörper-Wirkstoff-KonjugateL01FRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere monoklonale Antikörper und Antikörper-Wirkstoff-KonjugateL01FXRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AmivantamabL01FX18Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Belantamab mafodotinL01FX15Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BermekimabL01FX11Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BlinatumomabL01FX07Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Brentuximab vedotinL01FX05Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CatumaxomabL01FX03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Dinutuximab betaL01FX06Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in EdrecolomabL01FX01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ElotuzumabL01FX08Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Enfortumab vedotinL01FX13Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Gemtuzumab ozogamicinL01FX02Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in IpilimumabL01FX04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MogamulizumabL01FX09Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in NaxitamabL01FX21Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in OlaratumabL01FX10Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Oportuzumab monatoxL01FX16Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Polatuzumab vedotinL01FX14Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in SabatolimabL01FX19Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Sacituzumab govitecanL01FX17Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TafasitamabL01FX12Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TremelimumabL01FX20Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "CD20 (Cluster of Differentiation 20)-InhibitorenL01FARef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ObinutuzumabL01FA03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in OfatumumabL01FA02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in RituximabL01FA01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "CD22 (Cluster of Differentiation 22)-InhibitorenL01FBRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Inotuzumab ozogamicinL01FB01Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Moxetumomab pasudotoxL01FB02Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "CD38 (Cluster of Differentiation 38)-InhibitorenL01FCRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DaratumumabL01FC01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in IsatuximabL01FC02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "EGFR (Epidermaler Wachstumsfaktor-Rezeptor)-InhibitorenL01FERef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CetuximabL01FE01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in NecitumumabL01FE03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PanitumumabL01FE02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "HER2 (Humaner epidermaler Wachstumsfaktor-Rezeptor 2)-InhibitorenL01FDRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MargetuximabL01FD06Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PertuzumabL01FD02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TrastuzumabL01FD01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Trastuzumab deruxtecanL01FD04Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Trastuzumab duocarmazinL01FD05Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Trastuzumab emtansinL01FD03Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "PD-1/PDL-1 (Programmed Cell Death-1-Rezeptor/Programmed Cell Death-Ligand-1)-InhibitorenL01FFRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AtezolizumabL01FF05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AvelumabL01FF04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CemiplimabL01FF06Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DostarlimabL01FF07Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DurvalumabL01FF03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in NivolumabL01FF01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PembrolizumabL01FF02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ProlgolimabL01FF08Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in RetifanlimabL01FF10Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TislelizumabL01FF09Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "VEGF/VEGFR (Vaskulärer endothelialer Wachstumsfaktor)-InhibitorenL01FGRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BevacizumabL01FG01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in RamucirumabL01FG02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Pflanzliche Alkaloide Und Andere Natürliche MittelL01CRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere pflanzliche Alkaloide und natürliche MittelL01CXRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TrabectedinL01CX01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Colchicin-DerivateL01CCRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DemecolcinL01CC01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Homöopathische und anthroposophische MittelL01CHRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MistelkrautL01CH01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Pflanzliche MittelL01CPRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere pflanzliche Mittel, KombinationenL01CP50Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MistelkrautL01CP01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in VenusfliegenfalleL01CP02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Podophyllotoxin-DerivateL01CBRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in EtoposidL01CB01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TeniposidL01CB02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TaxaneL01CDRef) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CabazitaxelL01CD04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DocetaxelL01CD02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PaclitaxelL01CD01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Paclitaxel und EncequidarL01CD51Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PaclitaxelpoliglumexL01CD03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Topoisomerase-1 (TOP-1)-InhibitorenL01CERef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BelotecanL01CE04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Etirinotecan pegolL01CE03Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in IrinotecanL01CE02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TopotecanL01CE01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Vinka-Alkaloide und AnalogaL01CARef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in VinblastinL01CA01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in VincristinL01CA02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in VindesinL01CA03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in VinfluninL01CA05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in VinorelbinL01CA04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in VintafolidL01CA06Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Proteinkinase-InhibitorenL01ERef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Anaplastische Lymphomkinase (ALK)-InhibitorenL01EDRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AlectinibL01ED03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BrigatinibL01ED04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CeritinibL01ED02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CrizotinibL01ED01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in LorlatinibL01ED05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere Proteinkinase-InhibitorenL01EXRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AvapritinibL01EX18Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CabozantinibL01EX07Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CapmatinibL01EX17Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in EntrectinibL01EX14Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in GilteritinibL01EX13Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in LarotrectinibL01EX12Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in LenvatinibL01EX08Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MasitinibL01EX06Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MidostaurinL01EX10Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in NintedanibL01EX09Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PazopanibL01EX03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PexidartinibL01EX15Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PralsetinibL01EX23Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in QuizartinibL01EX11Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in RegorafenibL01EX05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in RipretinibL01EX19Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in SelpercatinibL01EX22Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in SorafenibL01EX02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in SunitinibL01EX01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in SurufatinibL01EX24Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TepotinibL01EX21Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in VandetanibL01EX04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "BCR-ABL-Tyrosinkinase-InhibitorenL01EARef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AsciminibL01EA06Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BosutinibL01EA04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DasatinibL01EA02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ImatinibL01EA01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in NilotinibL01EA03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PonatinibL01EA05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "BRAF-Serin-Threoninkinase-InhibitorenL01ECRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DabrafenibL01EC02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in EncorafenibL01EC03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in VemurafenibL01EC01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Bruton-Tyrosinkinase (BTK)-InhibitorenL01ELRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AcalabrutinibL01EL02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in IbrutinibL01EL01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ZanubrutinibL01EL03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Cyclin-abhängige Kinasen (CDK)-InhibitorenL01EFRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AbemaciclibL01EF03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PalbociclibL01EF01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in RibociclibL01EF02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Epidermaler Wachstumsfaktor-Rezeptor (EGFR)-Tyrosinkinase-InhibitorenL01EBRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AfatinibL01EB03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DacomitinibL01EB07Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ErlotinibL01EB02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in GefitinibL01EB01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in IcotinibL01EB08Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in LazertinibL01EB09Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MobocertinibL01EB10Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in OlmutinibL01EB06Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in OsimertinibL01EB04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in RociletinibL01EB05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Fibroblasten-Wachstumsfaktor-Rezeptor (FGFR)-Tyrosinkinase-InhibitorenL01ENRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ErdafitinibL01EN01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in FutibatinibL01EN04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in InfigratinibL01EN03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PemigatinibL01EN02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Humaner epidermaler Wachstumsfaktor-Rezeptor 2 (HER2)-Tyrosinkinase-InhibitorenL01EHRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in LapatinibL01EH01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in NeratinibL01EH02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TucatinibL01EH03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Janus-assoziierte Kinase (JAK)-InhibitorenL01EJRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in FedratinibL01EJ02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PacritinibL01EJ03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in RuxolitinibL01EJ01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Mammalian target of rapamycin (mTOR)-Kinase-InhibitorenL01EGRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in EverolimusL01EG02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in RidaforolimusL01EG03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TemsirolimusL01EG01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Mitogen-aktivierte Proteinkinase (MEK)-InhibitorenL01EERef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BinimetinibL01EE03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CobimetinibL01EE02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in SelumetinibL01EE04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TrametinibL01EE01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Phosphatidylinositol-3-kinase (Pi3K)-InhibitorenL01EMRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AlpelisibL01EM03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CopanlisibL01EM02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DuvelisibL01EM04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in IdelalisibL01EM01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ParsaclisibL01EM05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Vaskulärer endothelialer Wachstumsfaktor-Rezeptor (VEGFR)-Tyrosinkinase-InhibitorenL01EKRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AxitinibL01EK01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CediranibL01EK02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TivozanibL01EK03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Zytotoxische Antibiotika Und Verwandte SubstanzenL01DRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ActinomycineL01DARef) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DactinomycinL01DA01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere zytotoxische AntibiotikaL01DCRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BleomycinL01DC01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in IxabepilonL01DC04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MitomycinL01DC03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PlicamycinL01DC02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Anthracycline und verwandte SubstanzenL01DBRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AclarubicinL01DB04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AmrubicinL01DB10Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DaunorubicinL01DB02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DoxorubicinL01DB01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in EpirubicinL01DB03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in IdarubicinL01DB06Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MitoxantronL01DB07Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PirarubicinL01DB08Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PixantronL01DB11Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ValrubicinL01DB09Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ZorubicinL01DB05Ref)) and
  (exists (from [MedicationAdministration] M
    where M.medication.reference in "Endokrine TherapieL02Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Hormonantagonisten Und Verwandte MittelL02BRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere Hormonantagonisten und verwandte MittelL02BXRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AbarelixL02BX01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AbirateronL02BX03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DegarelixL02BX02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in RelugolixL02BX04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AntiandrogeneL02BBRef) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ApalutamidL02BB05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BicalutamidL02BB03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DarolutamidL02BB06Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in EnzalutamidL02BB04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in FlutamidL02BB01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in NilutamidL02BB02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AntiestrogeneL02BARef) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in FulvestrantL02BA03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TamoxifenL02BA01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ToremifenL02BA02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Aromatase-InhibitorenL02BGRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AminoglutethimidL02BG01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AnastrozolL02BG03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ExemestanL02BG06Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in FormestanL02BG02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in LetrozolL02BG04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TestolactonL02BG09Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in VorozolL02BG05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Hormone Und Verwandte MittelL02ARef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere HormoneL02AXRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in EstrogeneL02AARef) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ChlorotrianisenL02AA05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DiethylstilbestrolL02AA01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in EthinylestradiolL02AA03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in FosfestrolL02AA04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PolyestradiolphosphatL02AA02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in GestageneL02ABRef) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in GestonoronL02AB03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MedroxyprogesteronL02AB02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MegestrolL02AB01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Gonadotropin-Releasing-Hormon-AnalogaL02AERef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BuserelinL02AE01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in GoserelinL02AE03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in HistrelinL02AE05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in LeuprorelinL02AE02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Leuprorelin und BicalutamidL02AE51Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TriptorelinL02AE04Ref)) and
  (exists (from [MedicationAdministration] M
    where M.medication.reference in AnthelminthikaP02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BandwurmmittelP02DRef) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere BandwurmmittelP02DXRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DesaspidinP02DX01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DichlorophenP02DX02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Salicylsäure-DerivateP02DARef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in NiclosamidP02DA01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in NematodenmittelP02CRef) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere NematodenmittelP02CXRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere Nematodenmittel, KombinationenP02CX50Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BepheniumP02CX02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MoxidectinP02CX03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PyrviniumP02CX01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AvermektineP02CFRef) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in IvermectinP02CF01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Benzimidazol-DerivateP02CARef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AlbendazolP02CA03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CiclobendazolP02CA04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in FenbendazolP02CA06Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in FlubendazolP02CA05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MebendazolP02CA01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Mebendazol, KombinationenP02CA51Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TiabendazolP02CA02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Imidazothiazol-DerivateP02CERef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in LevamisolP02CE01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Piperazin und DerivateP02CBRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DiethylcarbamazinP02CB02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PiperazinP02CB01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Tetrahydropyrimidin-DerivateP02CCRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in OxantelP02CC02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PyrantelP02CC01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TrematodenmittelP02BRef) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere TrematodenmittelP02BXRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BithionolP02BX01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in NiridazolP02BX02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in StibophenP02BX03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TriclabendazolP02BX04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Chinolin-Derivate und verwandte SubstanzenP02BARef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in OxamniquinP02BA02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PraziquantelP02BA01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Organophosphat-VerbindungenP02BBRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MetrifonatP02BB01Ref))

define Exclusion:
  (exists [Procedure: Code '3-90...3-90' from oops] or
  exists [Procedure: Code '3-90' from oops] or
  exists [Procedure: Code '3-903' from oops] or
  exists [Procedure: Code '3-901' from oops] or
  exists [Procedure: Code '3-900' from oops] or
  exists [Procedure: Code '3-902' from oops]) and
  (exists [Condition: Code 'E11' from icd10] or
  exists [Condition: Code 'E11.3' from icd10] or
  exists [Condition: Code 'E11.31' from icd10] or
  exists [Condition: Code 'E11.30' from icd10] or
  exists [Condition: Code 'E11.1' from icd10] or
  exists [Condition: Code 'E11.11' from icd10] or
  exists [Condition: Code 'E11.0' from icd10] or
  exists [Condition: Code 'E11.01' from icd10] or
  exists [Condition: Code 'E11.7' from icd10] or
  exists [Condition: Code 'E11.75' from icd10] or
  exists [Condition: Code 'E11.74' from icd10] or
  exists [Condition: Code 'E11.73' from icd10] or
  exists [Condition: Code 'E11.72' from icd10] or
  exists [Condition: Code 'E11.4' from icd10] or
  exists [Condition: Code 'E11.41' from icd10] or
  exists [Condition: Code 'E11.40' from icd10] or
  exists [Condition: Code 'E11.8' from icd10] or
  exists [Condition: Code 'E11.81' from icd10] or
  exists [Condition: Code 'E11.80' from icd10] or
  exists [Condition: Code 'E11.2' from icd10] or
  exists [Condition: Code 'E11.21' from icd10] or
  exists [Condition: Code 'E11.20' from icd10] or
  exists [Condition: Code 'E11.5' from icd10] or
  exists [Condition: Code 'E11.51' from icd10] or
  exists [Condition: Code 'E11.50' from icd10] or
  exists [Condition: Code 'E11.6' from icd10] or
  exists [Condition: Code 'E11.61' from icd10] or
  exists [Condition: Code 'E11.60' from icd10] or
  exists [Condition: Code 'E11.9' from icd10] or
  exists [Condition: Code 'E11.91' from icd10] or
  exists [Condition: Code 'E11.90' from icd10]) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Husten- Und ErkältungsmittelR05Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere Zubereitungen Gegen ErkältungskrankheitenR05XRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Analgetika-haltige Mittel gegen ErkältungskrankheitenR05XARef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Acetylsalicylsäure und PseudoephedrinR05XA22Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Acetylsalicylsäure, KombinationenR05XA02Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Aminophenazon, KombinationenR05XA05Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Ethenzamid, KombinationenR05XA04Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Metamizol, KombinationenR05XA07Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Natriumsalicylat, KombinationenR05XA06Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Paracetamol, KombinationenR05XA01Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Phenacetin, KombinationenR05XA12Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Phenazon, KombinationenR05XA08Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Phenylbutazon, KombinationenR05XA10Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Propyphenazon, KombinationenR05XA03Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Salicylamid, KombinationenR05XA09Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere Mittel gegen ErkältungskrankheitenR05XCRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Enzym-haltige KombinationenR05XC01Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Pflanzliche Kombinationen mit anderen MittelnR05XC02Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere pflanzliche Mittel gegen ErkältungskrankheitenR05XPRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere pflanzliche Mittel gegen Erkältungskrankheiten, KombinationenR05XP50Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Echinacea-purpurea-PresssaftR05XP01Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Homöopathische und anthroposophische Mittel gegen ErkältungskrankheitenR05XHRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CinnabarisR05XH04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Echinacea angustifolia, KombinationenR05XH51Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Ferrum phosphoricumR05XH02Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Ferrum phosphoricum, KombinationenR05XH52Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in KombinationenR05XH20Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Antitussiva Und Expektoranzien, KombinationenR05FRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere Antitussiva und ExpektoranzienR05FBRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Antitussiva und ExpektoranzienR05FB02Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Antitussiva und MukolytikaR05FB01Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Homöopathische und anthroposophische Antitussiva und ExpektoranzienR05FHRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in KombinationenR05FH20Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in VerschiedeneR05FH10Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Opium-Derivate und ExpektoranzienR05FARef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Opium-Derivate und ExpektoranzienR05FA02Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Opium-Derivate und MukolytikaR05FA01Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Pflanzliche Antitussiva und ExpektoranzienR05FPRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Pflanzliche Antitussiva und Expektoranzien, KombinationenR05FP30Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Antitussiva Und Expektoranzien, Kombinationen Mit AntibiotikaR05GRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Antitussiva und AntibiotikaR05GARef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Doxycyclin, KombinationenR05GA01Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Tetracyclin, KombinationenR05GA02Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Expektoranzien und AntibiotikaR05GBRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Ampicillin, KombinationenR05GB05Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Cefaclor, KombinationenR05GB06Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Doxycyclin mit AmbroxolR05GB01Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Doxycyclin, KombinationenR05GB51Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Erythromycin, KombinationenR05GB07Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Metacyclin, KombinationenR05GB08Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Oxytetracyclin, KombinationenR05GB03Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Tetracyclin, KombinationenR05GB02Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Thiamphenicol, KombinationenR05GB04Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Expektoranzien und SulfonamideR05GCRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Sulfadiazin, KombinationenR05GC01Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Sulfamethoxazol und TrimethoprimR05GC02Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Sulfanilamid, KombinationenR05GC03Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Antitussiva, Exkl. Kombinationen Mit ExpektoranzienR05DRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere AntitussivaR05DBRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BenproperinR05DB02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BenzonatatR05DB01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BibenzoniumbromidR05DB12Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ButamiratR05DB13Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ButetamatR05DB30Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Butetamat, KombinationenR05DB80Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ClobutinolR05DB03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Clobutinol, KombinationenR05DB53Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ClofedanolR05DB10Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CloperastinR05DB21Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DibunatR05DB16Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DimethoxanatR05DB28Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DropropizinR05DB19Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DroxypropinR05DB17Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in FedrilatR05DB14Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in FominobenR05DB41Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in GefapixantR05DB29Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in IsoaminilR05DB04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Isoaminil, KombinationenR05DB54Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in KombinationenR05DB20Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in LevodropropizinR05DB27Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MenadiolR05DB31Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MeprotixolR05DB22Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MorclofonR05DB25Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in NepinalonR05DB26Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in OxeladinR05DB09Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in OxolaminR05DB07Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PentoxyverinR05DB05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Pentoxyverin, KombinationenR05DB55Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PipazetatR05DB11Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PiperidionR05DB23Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PrenoxdiazinR05DB18Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Prenoxdiazin, KombinationenR05DB68Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TipepidinR05DB24Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ZipeprolR05DB15Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Opium-Alkaloide und DerivateR05DARef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AcetyldihydrocodeinR05DA12Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere Opium-Alkaloide und Derivate, KombinationenR05DA50Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CodeinR05DA04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Codein, KombinationenR05DA54Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DextromethorphanR05DA09Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Dextromethorphan, KombinationenR05DA59Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DihydrocodeinR05DA14Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Dihydrocodein, KombinationenR05DA64Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DimemorfanR05DA11Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in EthylmorphinR05DA01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in HydrocodonR05DA03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in KombinationenR05DA20Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in NicocodinR05DA13Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in NormethadonR05DA06Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Normethadon, KombinationenR05DA56Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in NoscapinR05DA07Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Opium-Alkaloide mit MorphinR05DA05Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PholcodinR05DA08Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ThebaconR05DA10Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Pflanzliche AntitussivaR05DPRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Eibischwurzel und -blätterR05DP05Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "HuflattichblätterR05DP07Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Isländisches MoosR05DP02Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in SonnentaukrautR05DP01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in SpitzwegerichR05DP04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Expektoranzien, Exkl. Kombinationen Mit AntitussivaR05CRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ExpektoranzienR05CARef) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Altheae radix*R05CA05Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere Expektoranzien, KombinationenR05CA50Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AntimonpentasulfidR05CA07Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CaseinhydrolysatR05CA26Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CineolR05CA13Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Emser SalzR05CA22Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in GuaifenesinR05CA03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in GuajacolsulfonatR05CA09Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Hederae helicis folium*R05CA12Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Ipecacuanha*R05CA04Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in KaliumiodidR05CA02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in KombinationenR05CA10Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in KreosotR05CA08Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in LevoverbenonR05CA11Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Senega*R05CA06Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in TyloxapolR05CA01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Tyloxapol, KombinationenR05CA51Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Homöopathische und anthroposophische ExpektoranzienR05CHRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Cuprum aceticumR05CH01Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in KombinationenR05CH20Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Kombinationen mit anderen MittelnR05CH50Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MukolytikaR05CBRef) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AcetylcysteinR05CB01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AmbroxolR05CB06Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere Mukolytika, KombinationenR05CB50Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BromhexinR05CB02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Bromhexin, KombinationenR05CB52Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in CarbocisteinR05CB03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DomiodolR05CB08Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Dornase alfa (Desoxyribonuclease)R05CB13Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in EprazinonR05CB04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ErdosteinR05CB15Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in KombinationenR05CB10Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in LetosteinR05CB09Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MannitolR05CB16Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MesnaR05CB05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in NeltenexinR05CB14Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in SobrerolR05CB07Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in SteproninR05CB11Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Pflanzliche ExpektoranzienR05CPRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AndornkrautR05CP16Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AsarumwurzelstockR05CP04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "EfeublätterR05CP02Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "EukalyptusölR05CP09Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Eukalyptusöl, KombinationenR05CP59Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in IpecacuanhaR05CP11Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in KombinationenR05CP30Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "NiauliölR05CP10Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PelargoniumwurzelR05CP05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PrimelwurzelR05CP03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in SenegawurzelR05CP13Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "SpikölR05CP08Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "SüßholzwurzelR05CP17Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ThymiankrautR05CP01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Thymiankraut, KombinationenR05CP51Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in WollblumenR05CP15Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere Mittel Für Den RespirationstraktR07Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere Mittel Für Den RespirationstraktR07ARef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere homöopathische und anthroposophische Mittel für den RespirationstraktR07AHRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in KombinationenR07AH20Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in VerschiedeneR07AH10Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere Mittel für den RespirationstraktR07AXRef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in IvacaftorR07AX02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Ivacaftor und LumacaftorR07AX30Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Ivacaftor und TezacaftorR07AX31Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Ivacaftor, Tezacaftor und ElexacaftorR07AX32Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in StickoxidR07AX01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AtemstimulanzienR07ABRef) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AlmitrinR07AB07Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Andere Atemstimulanzien, KombinationenR07AB50Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in BemegridR07AB05Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DimeflinR07AB08Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in DoxapramR07AB01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in EtamivanR07AB04Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in MepixanoxR07AB09Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in NikethamidR07AB02Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Nikethamid, KombinationenR07AB52Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PentetrazolR07AB03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Pentetrazol, KombinationenR07AB53Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in PrethcamidR07AB06Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Surfactant-PräparateR07AARef") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in AmbroxolR07AA03Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in ColfoscerilpalmitatR07AA01Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in KombinationenR07AA30Ref) or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Natürliche Phospholipide aus RinderlungeR07AA05Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Natürliche Phospholipide aus SchweinelungeR07AA04Ref") or
  exists (from [MedicationAdministration] M
    where M.medication.reference in "Natürliche Phospholipide*R07AA02Ref")

define InInitialPopulation:
  Inclusion and
  not Exclusion
